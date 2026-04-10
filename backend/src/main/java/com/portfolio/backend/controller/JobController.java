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
                            // Enrich with fresh Redis status (may be more current than DB snapshot)
                            JobStatus liveStatus = jobStateService.getStatus(jobId);
                            if (liveStatus != null && liveStatus != job.getStatus()) {
                                job.setStatus(liveStatus);
                            }
                            return ResponseEntity.ok(toDto(job));
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()))
                .orElse(ResponseEntity.status(401).build());
    }

    /** List all jobs for the authenticated user, most recent first. */
    @GetMapping
    public ResponseEntity<?> listJobs(OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> {
                    List<AnalysisJob> jobs = jobRepository.findByUserOrderByCreatedAtDesc(user);
                    // Enrich each with live Redis status
                    jobs.forEach(job -> {
                        JobStatus liveStatus = jobStateService.getStatus(job.getId());
                        if (liveStatus != null && liveStatus != job.getStatus()) {
                            job.setStatus(liveStatus);
                        }
                    });
                    return ResponseEntity.ok(jobs.stream().map(this::toDto).toList());
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
