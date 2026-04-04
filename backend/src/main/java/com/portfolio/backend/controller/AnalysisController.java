package com.portfolio.backend.controller;

import com.portfolio.backend.entity.GeneratedContent;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.repository.UserRepository;
import com.portfolio.backend.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final UserRepository userRepository;

    @PostMapping("/repos/{repoId}/analyze")
    public ResponseEntity<?> analyze(@PathVariable UUID repoId, OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> {
                    List<GeneratedContent> content = analysisService.analyze(repoId, user);
                    return ResponseEntity.ok(content.stream().map(this::toDto).toList());
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/projects/{repoId}/content")
    public ResponseEntity<?> getContent(@PathVariable UUID repoId, OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> {
                    List<GeneratedContent> content = analysisService.getContent(repoId, user);
                    return ResponseEntity.ok(content.stream().map(this::toDto).toList());
                })
                .orElse(ResponseEntity.status(401).build());
    }

    private Optional<User> resolveUser(OAuth2AuthenticationToken auth) {
        if (auth == null) return Optional.empty();
        Long githubId = ((Number) auth.getPrincipal().getAttribute("id")).longValue();
        return userRepository.findByGithubId(githubId);
    }

    private Map<String, Object> toDto(GeneratedContent content) {
        return Map.of(
            "id", content.getId(),
            "contentType", content.getContentType(),
            "generatedText", content.getGeneratedText(),
            "createdAt", content.getCreatedAt()
        );
    }
}
