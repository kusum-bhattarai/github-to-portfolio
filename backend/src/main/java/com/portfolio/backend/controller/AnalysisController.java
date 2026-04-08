package com.portfolio.backend.controller;

import com.portfolio.backend.entity.AnalysisJob;
import com.portfolio.backend.entity.EditedContent;
import com.portfolio.backend.entity.GeneratedContent;
import com.portfolio.backend.entity.JobStatus;
import com.portfolio.backend.entity.Repository;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.repository.AnalysisJobRepository;
import com.portfolio.backend.repository.EditedContentRepository;
import com.portfolio.backend.repository.RepositoryRepository;
import com.portfolio.backend.repository.UserRepository;
import com.portfolio.backend.service.AnalysisService;
import com.portfolio.backend.service.AsyncAnalysisWorker;
import com.portfolio.backend.service.JobStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AsyncAnalysisWorker asyncWorker;
    private final JobStateService jobStateService;
    private final AnalysisJobRepository jobRepository;
    private final RepositoryRepository repositoryRepository;
    private final EditedContentRepository editedContentRepository;
    private final UserRepository userRepository;

    // ── Single repo analyze (reanalyze from ResultsPage) ─────────────────────

    @PostMapping("/repos/{repoId}/analyze")
    public ResponseEntity<?> analyze(@PathVariable UUID repoId, OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> {
                    Repository repo = repositoryRepository.findById(repoId).orElse(null);
                    if (repo == null || !repo.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(404).<Object>build();
                    }
                    AnalysisJob job = submitJob(user, repo);
                    return ResponseEntity.ok(toJobDto(job));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    // ── Batch analyze (from Dashboard) ───────────────────────────────────────

    @PostMapping("/repos/analyze/batch")
    public ResponseEntity<?> analyzeBatch(
            @RequestBody Map<String, List<String>> body,
            OAuth2AuthenticationToken auth) {

        List<String> repoIdStrings = body.getOrDefault("repoIds", List.of());
        if (repoIdStrings.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repoIds must not be empty"));
        }

        return resolveUser(auth)
                .map(user -> {
                    List<Map<String, Object>> jobs = new ArrayList<>();
                    for (String idStr : repoIdStrings) {
                        try {
                            UUID repoId = UUID.fromString(idStr);
                            Repository repo = repositoryRepository.findById(repoId).orElse(null);
                            if (repo == null || !repo.getUser().getId().equals(user.getId())) continue;
                            jobs.add(toJobDto(submitJob(user, repo)));
                        } catch (IllegalArgumentException ignored) { /* bad UUID */ }
                    }
                    return ResponseEntity.ok(jobs);
                })
                .orElse(ResponseEntity.status(401).build());
    }

    // ── Content retrieval ─────────────────────────────────────────────────────

    @GetMapping("/projects/{repoId}/content")
    public ResponseEntity<?> getContent(@PathVariable UUID repoId, OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> {
                    List<GeneratedContent> content = analysisService.getContent(repoId, user);
                    List<EditedContent> edits = editedContentRepository.findByGeneratedContentRepositoryId(repoId);
                    return ResponseEntity.ok(content.stream().map(c -> toContentDto(c, edits)).toList());
                })
                .orElse(ResponseEntity.status(401).build());
    }

    // ── Workspace ─────────────────────────────────────────────────────────────

    @GetMapping("/projects")
    public ResponseEntity<?> getProjects(OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> ResponseEntity.ok(analysisService.getProjects(user)))
                .orElse(ResponseEntity.status(401).build());
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    @PutMapping("/projects/{repoId}/content/{contentId}")
    public ResponseEntity<?> saveEdit(
            @PathVariable UUID repoId,
            @PathVariable UUID contentId,
            @RequestBody Map<String, String> body,
            OAuth2AuthenticationToken auth) {

        String newText = body.get("text");
        if (newText == null || newText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        return resolveUser(auth)
                .map(user -> {
                    EditedContent saved = analysisService.saveEdit(contentId, newText, user);
                    return ResponseEntity.ok(Map.of(
                            "id", saved.getId(),
                            "editedText", saved.getEditedText(),
                            "updatedAt", saved.getUpdatedAt()
                    ));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AnalysisJob submitJob(User user, Repository repo) {
        AnalysisJob job = AnalysisJob.builder()
                .user(user)
                .repository(repo)
                .status(JobStatus.QUEUED)
                .build();
        AnalysisJob saved = jobRepository.save(job);
        jobStateService.seed(saved.getId(), JobStatus.QUEUED);
        asyncWorker.run(saved.getId(), repo.getId(), user.getId());
        return saved;
    }

    private Optional<User> resolveUser(OAuth2AuthenticationToken auth) {
        if (auth == null) return Optional.empty();
        Long githubId = ((Number) auth.getPrincipal().getAttribute("id")).longValue();
        return userRepository.findByGithubId(githubId);
    }

    private Map<String, Object> toJobDto(AnalysisJob job) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("jobId", job.getId());
        dto.put("repoId", job.getRepository().getId());
        dto.put("repoName", job.getRepository().getName());
        dto.put("status", job.getStatus().name());
        dto.put("createdAt", job.getCreatedAt());
        dto.put("startedAt", job.getStartedAt());
        dto.put("completedAt", job.getCompletedAt());
        dto.put("errorMessage", job.getErrorMessage());
        return dto;
    }

    private Map<String, Object> toContentDto(GeneratedContent content, List<EditedContent> allEdits) {
        String editedText = allEdits.stream()
                .filter(e -> e.getGeneratedContent().getId().equals(content.getId()))
                .map(EditedContent::getEditedText)
                .findFirst().orElse(null);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", content.getId());
        dto.put("contentType", content.getContentType());
        dto.put("generatedText", content.getGeneratedText());
        dto.put("editedText", editedText);
        dto.put("isEdited", editedText != null);
        dto.put("createdAt", content.getCreatedAt());
        return dto;
    }
}
