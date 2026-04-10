package com.portfolio.backend.controller;

import com.portfolio.backend.entity.Repository;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.repository.UserRepository;
import com.portfolio.backend.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRepos(OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> ResponseEntity.ok(repositoryService.getRepos(user).stream().map(this::toDto).toList()))
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/sync")
    public ResponseEntity<List<Map<String, Object>>> syncRepos(OAuth2AuthenticationToken auth) {
        return resolveUser(auth)
                .map(user -> ResponseEntity.ok(repositoryService.syncRepos(user).stream().map(this::toDto).toList()))
                .orElse(ResponseEntity.status(401).build());
    }

    private java.util.Optional<User> resolveUser(OAuth2AuthenticationToken auth) {
        if (auth == null) return java.util.Optional.empty();
        Long githubId = ((Number) auth.getPrincipal().getAttribute("id")).longValue();
        return userRepository.findByGithubId(githubId);
    }

    private Map<String, Object> toDto(Repository repo) {
        return Map.of(
            "id", repo.getId(),
            "githubRepoId", repo.getGithubRepoId(),
            "name", repo.getName(),
            "fullName", repo.getFullName(),
            "description", repo.getDescription() != null ? repo.getDescription() : "",
            "primaryLanguage", repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : "",
            "stars", repo.getStars(),
            "forks", repo.getForks(),
            "topics", repo.getTopics(),
            "htmlUrl", repo.getHtmlUrl() != null ? repo.getHtmlUrl() : ""
        );
    }
}
