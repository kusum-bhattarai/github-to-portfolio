package com.portfolio.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final int PAGE_SIZE = 100;

    private final RestTemplate restTemplate;

    public List<Map<String, Object>> fetchUserRepos(String accessToken) {
        List<Map<String, Object>> allRepos = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = GITHUB_API_BASE + "/user/repos?visibility=public&sort=updated&per_page=" + PAGE_SIZE + "&page=" + page;

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    buildRequest(accessToken),
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> page_repos = response.getBody();
            if (page_repos == null || page_repos.isEmpty()) break;

            allRepos.addAll(page_repos);
            if (page_repos.size() < PAGE_SIZE) break;
            page++;
        }

        log.debug("Fetched {} repos from GitHub", allRepos.size());
        return allRepos;
    }

    private HttpEntity<Void> buildRequest(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return new HttpEntity<>(headers);
    }
}
