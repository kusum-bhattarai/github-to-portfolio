package com.portfolio.backend.controller;

import com.portfolio.backend.entity.EditedContent;
import com.portfolio.backend.entity.GeneratedContent;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.repository.EditedContentRepository;
import com.portfolio.backend.repository.UserRepository;
import com.portfolio.backend.service.AnalysisService;
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
    private final EditedContentRepository editedContentRepository;
    private final UserRepository userRepository;

    // ── Analysis ─────────────────────────────────────────────────────────────

    @PostMapping("/repos/{repoId}/analyze")
    public ResponseEntity<?> analyze(@PathVariable UUID repoId, OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> {
                    List<GeneratedContent> content = analysisService.analyze(repoId, user);
                    List<EditedContent> edits = editedContentRepository.findByGeneratedContentRepositoryId(repoId);
                    return ResponseEntity.ok(content.stream().map(c -> toDto(c, edits)).toList());
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
                    return ResponseEntity.ok(content.stream().map(c -> toDto(c, edits)).toList());
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

    private Optional<User> resolveUser(OAuth2AuthenticationToken auth) {
        if (auth == null) return Optional.empty();
        Long githubId = ((Number) auth.getPrincipal().getAttribute("id")).longValue();
        return userRepository.findByGithubId(githubId);
    }

    private Map<String, Object> toDto(GeneratedContent content, List<EditedContent> allEdits) {
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
