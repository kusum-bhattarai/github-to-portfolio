package com.portfolio.backend.service;

import com.portfolio.backend.entity.Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Extracts structured evidence from a GitHub repository.
 * Pulls signals from README, file tree, and repo metadata
 * to build a RepoSnapshot before calling the LLM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceExtractor {

    private static final String GITHUB_API = "https://api.github.com";

    private final RestTemplate restTemplate;

    public record ExtractionResult(
            String readmeContent,
            List<String> detectedStack,
            Map<String, Object> signals
    ) {}

    public ExtractionResult extract(Repository repo, String accessToken) {
        String owner = repo.getFullName().split("/")[0];
        String repoName = repo.getName();

        String readme = fetchReadme(owner, repoName, accessToken);
        List<Map<String, Object>> tree = fetchFileTree(owner, repoName, accessToken);

        Map<String, Object> signals = detectSignals(tree, repo);
        List<String> stack = detectStack(tree, repo, signals);

        log.debug("Extracted signals for {}: {}", repo.getFullName(), signals);
        return new ExtractionResult(readme, stack, signals);
    }

    private String fetchReadme(String owner, String repo, String token) {
        try {
            String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/readme";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authHeaders(token),
                    new ParameterizedTypeReference<>() {}
            );
            if (response.getBody() != null && response.getBody().containsKey("content")) {
                String encoded = (String) response.getBody().get("content");
                byte[] decoded = Base64.getMimeDecoder().decode(encoded);
                return new String(decoded).substring(0, Math.min(new String(decoded).length(), 4000));
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("No README found for {}/{}", owner, repo);
        } catch (Exception e) {
            log.warn("Failed to fetch README for {}/{}: {}", owner, repo, e.getMessage());
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchFileTree(String owner, String repo, String token) {
        try {
            String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/git/trees/HEAD?recursive=0";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authHeaders(token),
                    new ParameterizedTypeReference<>() {}
            );
            if (response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody().getOrDefault("tree", List.of());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch file tree for {}/{}: {}", owner, repo, e.getMessage());
        }
        return List.of();
    }

    private Map<String, Object> detectSignals(List<Map<String, Object>> tree, Repository repo) {
        Set<String> filenames = new HashSet<>();
        Set<String> dirs = new HashSet<>();

        for (Map<String, Object> node : tree) {
            String path = (String) node.get("path");
            String type = (String) node.get("type");
            if (path == null) continue;
            String lower = path.toLowerCase();
            filenames.add(lower);
            if ("tree".equals(type)) dirs.add(lower);
        }

        Map<String, Object> signals = new LinkedHashMap<>();

        // Infrastructure
        signals.put("hasDocker", filenames.contains("dockerfile") || filenames.contains("docker-compose.yml") || filenames.contains("docker-compose.yaml"));
        signals.put("hasCI", dirs.contains(".github/workflows") || filenames.stream().anyMatch(f -> f.contains(".github/workflows")));
        signals.put("hasTests", filenames.stream().anyMatch(f -> f.contains("test") || f.contains("spec")));
        signals.put("hasMakefile", filenames.contains("makefile"));

        // Package managers / build tools
        signals.put("hasPackageJson", filenames.contains("package.json"));
        signals.put("hasRequirements", filenames.contains("requirements.txt") || filenames.contains("pyproject.toml") || filenames.contains("setup.py"));
        signals.put("hasPomXml", filenames.contains("pom.xml"));
        signals.put("hasGradleBuild", filenames.contains("build.gradle") || filenames.contains("build.gradle.kts"));
        signals.put("hasCargoToml", filenames.contains("cargo.toml"));
        signals.put("hasGoMod", filenames.contains("go.mod"));

        // Frontend signals
        signals.put("hasTailwind", filenames.contains("tailwind.config.js") || filenames.contains("tailwind.config.ts"));
        signals.put("hasVite", filenames.contains("vite.config.js") || filenames.contains("vite.config.ts"));
        signals.put("hasNextConfig", filenames.contains("next.config.js") || filenames.contains("next.config.ts") || filenames.contains("next.config.mjs"));

        // Database / backend signals
        signals.put("hasMigrations", dirs.stream().anyMatch(d -> d.contains("migration") || d.contains("migrations")));
        signals.put("hasEnvExample", filenames.contains(".env.example") || filenames.contains(".env.sample"));

        // Architecture signals
        signals.put("hasControllers", dirs.stream().anyMatch(d -> d.contains("controller") || d.contains("controllers")));
        signals.put("hasServices", dirs.stream().anyMatch(d -> d.contains("service") || d.contains("services")));
        signals.put("hasModels", dirs.stream().anyMatch(d -> d.contains("model") || d.contains("models") || d.contains("entity") || d.contains("entities")));

        // Repo metadata
        signals.put("stars", repo.getStars());
        signals.put("forks", repo.getForks());
        signals.put("topics", repo.getTopics());
        signals.put("primaryLanguage", repo.getPrimaryLanguage());

        return signals;
    }

    private List<String> detectStack(List<Map<String, Object>> tree, Repository repo, Map<String, Object> signals) {
        Set<String> stack = new LinkedHashSet<>();

        // Language
        if (repo.getPrimaryLanguage() != null) stack.add(repo.getPrimaryLanguage());

        // JS/TS frameworks
        if (Boolean.TRUE.equals(signals.get("hasPackageJson"))) {
            if (Boolean.TRUE.equals(signals.get("hasNextConfig"))) stack.add("Next.js");
            if (Boolean.TRUE.equals(signals.get("hasVite"))) stack.add("Vite");
            if (Boolean.TRUE.equals(signals.get("hasTailwind"))) stack.add("Tailwind CSS");
        }

        // Java frameworks
        if (Boolean.TRUE.equals(signals.get("hasPomXml"))) stack.add("Maven");
        if (Boolean.TRUE.equals(signals.get("hasGradleBuild"))) stack.add("Gradle");

        // Infrastructure
        if (Boolean.TRUE.equals(signals.get("hasDocker"))) stack.add("Docker");
        if (Boolean.TRUE.equals(signals.get("hasCI"))) stack.add("GitHub Actions");

        // Topics as additional stack signals
        if (repo.getTopics() != null) {
            Set<String> knownTech = Set.of(
                "react", "vue", "angular", "svelte", "nextjs", "express", "fastapi",
                "django", "flask", "spring", "springboot", "postgres", "postgresql",
                "mysql", "mongodb", "redis", "graphql", "rest", "grpc", "kubernetes",
                "terraform", "aws", "gcp", "azure"
            );
            for (String topic : repo.getTopics()) {
                if (knownTech.contains(topic.toLowerCase())) {
                    stack.add(capitalize(topic));
                }
            }
        }

        return new ArrayList<>(stack);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private HttpEntity<Void> authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return new HttpEntity<>(headers);
    }
}
