package com.portfolio.backend.service;

import com.portfolio.backend.entity.GitHubConnection;
import com.portfolio.backend.entity.Repository;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.repository.GitHubConnectionRepository;
import com.portfolio.backend.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final GitHubApiClient gitHubApiClient;
    private final TokenEncryptionService tokenEncryptionService;

    @Transactional
    public List<Repository> syncRepos(User user) {
        GitHubConnection connection = connectionRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("No GitHub connection found for user"));

        String accessToken = tokenEncryptionService.decrypt(connection.getEncryptedAccessToken());
        List<Map<String, Object>> rawRepos = gitHubApiClient.fetchUserRepos(accessToken);

        for (Map<String, Object> raw : rawRepos) {
            upsertRepository(user, raw);
        }

        log.info("Synced {} repos for user {}", rawRepos.size(), user.getUsername());
        return repositoryRepository.findByUserOrderByStarsDesc(user);
    }

    public List<Repository> getRepos(User user) {
        return repositoryRepository.findByUserOrderByStarsDesc(user);
    }

    @SuppressWarnings("unchecked")
    private void upsertRepository(User user, Map<String, Object> raw) {
        Long githubRepoId = ((Number) raw.get("id")).longValue();

        Repository repo = repositoryRepository.findByUserAndGithubRepoId(user, githubRepoId)
                .orElseGet(() -> Repository.builder().user(user).githubRepoId(githubRepoId).build());

        repo.setName((String) raw.get("name"));
        repo.setFullName((String) raw.get("full_name"));
        repo.setDescription((String) raw.get("description"));
        repo.setVisibility((String) raw.getOrDefault("visibility", "public"));
        repo.setHtmlUrl((String) raw.get("html_url"));
        repo.setStars(((Number) raw.getOrDefault("stargazers_count", 0)).intValue());
        repo.setForks(((Number) raw.getOrDefault("forks_count", 0)).intValue());

        if (raw.get("language") instanceof String lang) {
            repo.setPrimaryLanguage(lang);
        }

        List<String> topics = (List<String>) raw.get("topics");
        repo.setTopics(topics != null ? topics : List.of());

        repositoryRepository.save(repo);
    }
}
