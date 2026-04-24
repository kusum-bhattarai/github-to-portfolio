package com.portfolio.backend.controller;

import com.portfolio.backend.entity.AnalysisJob;
import com.portfolio.backend.entity.JobStatus;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.repository.AnalysisJobRepository;
import com.portfolio.backend.repository.UserRepository;
import com.portfolio.backend.service.JobStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final AnalysisJobRepository jobRepository;
    private final JobStateService jobStateService;
    private final UserRepository userRepository;

    /** Poll a single job — Redis hot path, DB fallback. */
    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable UUID jobId, OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> jobRepository.findByIdAndUser(jobId, user)
                        .map(job -> {
                            if (!job.getStatus().isTerminal()) {
                                JobStatus liveStatus = jobStateService.getStatus(jobId);
                                if (liveStatus != null && liveStatus != job.getStatus()) {
                                    job.setStatus(liveStatus);
                                }
                            }
                            return ResponseEntity.ok(toDto(job));
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()))
                .orElse(ResponseEntity.status(401).build());
    }

    /** List jobs for the authenticated user, most recent first. Paginated — defaults to page 0, size 20. */
    @GetMapping
    public ResponseEntity<?> listJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            OAuth2AuthenticationToken auth) {

        int safeSize = Math.min(size, 100); // cap page size
        return resolveUser(auth)
                .map(user -> {
                    var pageable = org.springframework.data.domain.PageRequest.of(page, safeSize);
                    var jobPage = jobRepository.findByUser(user, pageable);
                    // Enrich active jobs with live Redis status (skip terminal — DB wins)
                    jobPage.forEach(job -> {
                        if (!job.getStatus().isTerminal()) {
                            JobStatus liveStatus = jobStateService.getStatus(job.getId());
                            if (liveStatus != null && liveStatus != job.getStatus()) {
                                job.setStatus(liveStatus);
                            }
                        }
                    });
                    var body = java.util.Map.of(
                            "content", jobPage.stream().map(this::toDto).toList(),
                            "page", jobPage.getNumber(),
                            "size", jobPage.getSize(),
                            "totalElements", jobPage.getTotalElements(),
                            "totalPages", jobPage.getTotalPages(),
                            "last", jobPage.isLast()
                    );
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.status(401).build());
    }

    private Optional<User> resolveUser(OAuth2AuthenticationToken auth) {
        if (auth == null) return Optional.empty();
        Long githubId = ((Number) auth.getPrincipal().getAttribute("id")).longValue();
        return userRepository.findByGithubId(githubId);
    }

    private Map<String, Object> toDto(AnalysisJob job) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("jobId", job.getId());
        dto.put("repoId", job.getRepository().getId());
        dto.put("repoName", job.getRepository().getName());
        dto.put("repoFullName", job.getRepository().getFullName());
        dto.put("status", job.getStatus().name());
        dto.put("isActive", job.getStatus().isActive());
        dto.put("createdAt", job.getCreatedAt());
        dto.put("startedAt", job.getStartedAt());
        dto.put("completedAt", job.getCompletedAt());
        dto.put("errorMessage", job.getErrorMessage());
        return dto;
    }
}
