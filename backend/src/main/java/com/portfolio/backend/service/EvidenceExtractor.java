package com.portfolio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.backend.entity.Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 4 — Evidence Extraction Engine.
 *
 * Goes beyond file-existence signals: fetches and parses actual config file contents
 * (package.json, pom.xml, requirements.txt, Dockerfile, GitHub Actions), extracts
 * quantitative metrics from the GitHub API (commit count, contributors, language bytes),
 * and classifies the project type before passing everything to the LLM.
 *
 * This structured evidence pipeline is what separates this system from a naive LLM wrapper.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceExtractor {

    private static final String GITHUB_API = "https://api.github.com";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Result record ───────────────────────────────────────────────────────

    public record ExtractionResult(
            String readmeContent,
            List<String> detectedStack,
            Map<String, Object> signals,
            String projectType,
            Map<String, Object> parsedDependencies,
            Map<String, Object> quantitativeMetrics
    ) {}

    // ─── Main entry point ────────────────────────────────────────────────────

    public ExtractionResult extract(Repository repo, String accessToken) {
        String owner = repo.getFullName().split("/")[0];
        String repoName = repo.getName();

        log.info("Phase 4 evidence extraction starting for {}", repo.getFullName());

        String readme = fetchReadme(owner, repoName, accessToken);
        List<Map<String, Object>> tree = fetchFileTree(owner, repoName, accessToken);

        Map<String, Object> signals = detectSignals(tree, repo);
        Map<String, Object> parsedDeps = parseConfigFiles(owner, repoName, accessToken, tree, signals);
        Map<String, Object> metrics = fetchQuantitativeMetrics(owner, repoName, accessToken, tree);
        List<String> stack = buildStack(repo, signals, parsedDeps);
        String projectType = classifyProjectType(repo, signals, parsedDeps, stack);

        log.info("Extracted evidence for {} — type: {}, stack: {}, metrics: {}",
                repo.getFullName(), projectType, stack, metrics);

        return new ExtractionResult(readme, stack, signals, projectType, parsedDeps, metrics);
    }

    // ─── README ──────────────────────────────────────────────────────────────

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
                String text = new String(decoded);
                return text.substring(0, Math.min(text.length(), 4000));
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("No README for {}/{}", owner, repo);
        } catch (Exception e) {
            log.warn("Failed to fetch README for {}/{}: {}", owner, repo, e.getMessage());
        }
        return "";
    }

    // ─── File tree ───────────────────────────────────────────────────────────

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

    // ─── Boolean signals ─────────────────────────────────────────────────────

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

        Map<String, Object> s = new LinkedHashMap<>();

        // Infrastructure
        s.put("hasDocker", filenames.contains("dockerfile") || filenames.contains("docker-compose.yml") || filenames.contains("docker-compose.yaml"));
        s.put("hasDockerCompose", filenames.contains("docker-compose.yml") || filenames.contains("docker-compose.yaml"));
        s.put("hasCI", dirs.contains(".github/workflows") || filenames.stream().anyMatch(f -> f.contains(".github/workflows")));
        s.put("hasTests", filenames.stream().anyMatch(f -> f.contains("test") || f.contains("spec")));
        s.put("hasMakefile", filenames.contains("makefile"));
        s.put("hasKubernetes", filenames.stream().anyMatch(f -> f.contains("k8s") || f.contains("helm") || f.contains("kubernetes")));
        s.put("hasTerraform", filenames.stream().anyMatch(f -> f.endsWith(".tf")));

        // Package managers / build tools
        s.put("hasPackageJson", filenames.contains("package.json"));
        s.put("hasRequirements", filenames.contains("requirements.txt") || filenames.contains("pyproject.toml") || filenames.contains("setup.py"));
        s.put("hasPomXml", filenames.contains("pom.xml"));
        s.put("hasGradleBuild", filenames.contains("build.gradle") || filenames.contains("build.gradle.kts"));
        s.put("hasCargoToml", filenames.contains("cargo.toml"));
        s.put("hasGoMod", filenames.contains("go.mod"));

        // Frontend
        s.put("hasTailwind", filenames.contains("tailwind.config.js") || filenames.contains("tailwind.config.ts"));
        s.put("hasVite", filenames.contains("vite.config.js") || filenames.contains("vite.config.ts"));
        s.put("hasNextConfig", filenames.contains("next.config.js") || filenames.contains("next.config.ts") || filenames.contains("next.config.mjs"));
        s.put("hasWebpack", filenames.contains("webpack.config.js") || filenames.contains("webpack.config.ts"));

        // Database / backend
        s.put("hasMigrations", dirs.stream().anyMatch(d -> d.contains("migration") || d.contains("migrations")) || filenames.stream().anyMatch(f -> f.contains("flyway") || f.contains("liquibase")));
        s.put("hasEnvExample", filenames.contains(".env.example") || filenames.contains(".env.sample"));
        s.put("hasOpenApi", filenames.stream().anyMatch(f -> f.contains("openapi") || f.contains("swagger")));

        // Architecture layers
        s.put("hasControllers", dirs.stream().anyMatch(d -> d.contains("controller") || d.contains("controllers") || d.contains("handler") || d.contains("handlers")));
        s.put("hasServices", dirs.stream().anyMatch(d -> d.contains("service") || d.contains("services")));
        s.put("hasModels", dirs.stream().anyMatch(d -> d.contains("model") || d.contains("models") || d.contains("entity") || d.contains("entities")));
        s.put("hasRepositories", dirs.stream().anyMatch(d -> d.contains("repository") || d.contains("repositories") || d.contains("dao")));

        // Metadata
        s.put("stars", repo.getStars());
        s.put("forks", repo.getForks());
        s.put("topics", repo.getTopics());
        s.put("primaryLanguage", repo.getPrimaryLanguage());

        return s;
    }

    // ─── Config file parsing ─────────────────────────────────────────────────

    private Map<String, Object> parseConfigFiles(
            String owner, String repoName, String token,
            List<Map<String, Object>> tree, Map<String, Object> signals) {

        Map<String, Object> deps = new LinkedHashMap<>();

        Set<String> paths = tree.stream()
                .map(n -> ((String) n.getOrDefault("path", "")).toLowerCase())
                .collect(Collectors.toSet());

        if (Boolean.TRUE.equals(signals.get("hasPackageJson"))) {
            parsePackageJson(owner, repoName, token, deps);
        }
        if (Boolean.TRUE.equals(signals.get("hasPomXml"))) {
            parsePomXml(owner, repoName, token, deps);
        }
        if (Boolean.TRUE.equals(signals.get("hasRequirements"))) {
            parseRequirementsTxt(owner, repoName, token, deps, paths);
        }
        if (Boolean.TRUE.equals(signals.get("hasGradleBuild"))) {
            parseGradleBuild(owner, repoName, token, deps, paths);
        }
        if (Boolean.TRUE.equals(signals.get("hasDocker"))) {
            parseDockerfile(owner, repoName, token, deps);
        }
        if (Boolean.TRUE.equals(signals.get("hasCI"))) {
            parseGithubActions(owner, repoName, token, deps, tree);
        }

        return deps;
    }

    private void parsePackageJson(String owner, String repo, String token, Map<String, Object> out) {
        try {
            String content = fetchFileContent(owner, repo, "package.json", token);
            if (content == null) return;

            JsonNode root = objectMapper.readTree(content);
            Map<String, String> prodDeps = new LinkedHashMap<>();
            Map<String, String> devDeps = new LinkedHashMap<>();

            if (root.has("dependencies")) {
                root.get("dependencies").properties()
                        .forEach(e -> prodDeps.put(e.getKey(), e.getValue().asText()));
            }
            if (root.has("devDependencies")) {
                root.get("devDependencies").properties()
                        .forEach(e -> devDeps.put(e.getKey(), e.getValue().asText()));
            }

            // Detect key frameworks from dependencies
            List<String> frameworks = new ArrayList<>();
            Set<String> allDeps = new HashSet<>(prodDeps.keySet());
            allDeps.addAll(devDeps.keySet());

            Map<String, String> knownFrameworks = new LinkedHashMap<>();
            knownFrameworks.put("react", "React");
            knownFrameworks.put("next", "Next.js");
            knownFrameworks.put("vue", "Vue.js");
            knownFrameworks.put("@angular/core", "Angular");
            knownFrameworks.put("svelte", "Svelte");
            knownFrameworks.put("express", "Express.js");
            knownFrameworks.put("fastify", "Fastify");
            knownFrameworks.put("@nestjs/core", "NestJS");
            knownFrameworks.put("koa", "Koa.js");
            knownFrameworks.put("socket.io", "Socket.IO");
            knownFrameworks.put("graphql", "GraphQL");
            knownFrameworks.put("prisma", "Prisma");
            knownFrameworks.put("typeorm", "TypeORM");
            knownFrameworks.put("mongoose", "Mongoose");
            knownFrameworks.put("@tanstack/react-query", "TanStack Query");
            knownFrameworks.put("redux", "Redux");
            knownFrameworks.put("zustand", "Zustand");
            knownFrameworks.put("tailwindcss", "Tailwind CSS");
            knownFrameworks.put("vite", "Vite");
            knownFrameworks.put("jest", "Jest");
            knownFrameworks.put("vitest", "Vitest");
            knownFrameworks.put("typescript", "TypeScript");
            knownFrameworks.put("axios", "Axios");
            knownFrameworks.put("zod", "Zod");

            for (var entry : knownFrameworks.entrySet()) {
                if (allDeps.contains(entry.getKey())) {
                    String version = prodDeps.getOrDefault(entry.getKey(), devDeps.get(entry.getKey()));
                    frameworks.add(entry.getValue() + (version != null ? " " + version.replace("^", "").replace("~", "") : ""));
                }
            }

            out.put("npm", Map.of(
                    "productionDependencies", prodDeps.size(),
                    "devDependencies", devDeps.size(),
                    "totalDependencies", allDeps.size(),
                    "detectedFrameworks", frameworks,
                    "hasTypeScript", allDeps.contains("typescript"),
                    "hasTestFramework", allDeps.contains("jest") || allDeps.contains("vitest") || allDeps.contains("mocha"),
                    "packageName", root.path("name").asText(""),
                    "packageVersion", root.path("version").asText("")
            ));

            log.debug("Parsed package.json: {} prod deps, {} dev deps, frameworks: {}",
                    prodDeps.size(), devDeps.size(), frameworks);

        } catch (Exception e) {
            log.warn("Failed to parse package.json for {}/{}: {}", owner, repo, e.getMessage());
        }
    }

    private void parsePomXml(String owner, String repo, String token, Map<String, Object> out) {
        try {
            String content = fetchFileContent(owner, repo, "pom.xml", token);
            if (content == null) return;

            // Extract Spring Boot version from parent
            String springBootVersion = "";
            var parentMatch = java.util.regex.Pattern.compile(
                    "<parent>[\\s\\S]*?<artifactId>spring-boot-starter-parent</artifactId>[\\s\\S]*?<version>([^<]+)</version>[\\s\\S]*?</parent>"
            ).matcher(content);
            if (parentMatch.find()) springBootVersion = parentMatch.group(1).trim();

            // Extract all dependency artifactIds
            List<String> artifactIds = new ArrayList<>();
            var depPattern = java.util.regex.Pattern.compile("<artifactId>([^<]+)</artifactId>");
            var matcher = depPattern.matcher(content);
            boolean firstSkipped = false; // first is the project itself
            while (matcher.find()) {
                if (!firstSkipped) { firstSkipped = true; continue; }
                artifactIds.add(matcher.group(1).trim());
            }

            // Map to friendly framework names
            List<String> frameworks = new ArrayList<>();
            Map<String, String> knownMaven = new LinkedHashMap<>();
            knownMaven.put("spring-boot-starter-web", "Spring Web MVC");
            knownMaven.put("spring-boot-starter-data-jpa", "Spring Data JPA");
            knownMaven.put("spring-boot-starter-security", "Spring Security");
            knownMaven.put("spring-boot-starter-oauth2-client", "Spring OAuth2 Client");
            knownMaven.put("spring-boot-starter-data-redis", "Spring Data Redis");
            knownMaven.put("spring-boot-starter-validation", "Spring Validation");
            knownMaven.put("spring-boot-starter-webflux", "Spring WebFlux");
            knownMaven.put("spring-kafka", "Spring Kafka");
            knownMaven.put("flyway-core", "Flyway Migrations");
            knownMaven.put("liquibase-core", "Liquibase Migrations");
            knownMaven.put("postgresql", "PostgreSQL");
            knownMaven.put("h2", "H2 (in-memory DB)");
            knownMaven.put("lombok", "Lombok");
            knownMaven.put("mapstruct", "MapStruct");
            knownMaven.put("spring-boot-starter-test", "JUnit 5 / Spring Boot Test");
            knownMaven.put("mockito-core", "Mockito");
            knownMaven.put("testcontainers", "Testcontainers");
            knownMaven.put("spring-boot-starter-actuator", "Spring Actuator");
            knownMaven.put("micrometer-registry-prometheus", "Prometheus / Micrometer");

            for (String id : artifactIds) {
                if (knownMaven.containsKey(id)) frameworks.add(knownMaven.get(id));
            }

            out.put("maven", Map.of(
                    "totalDependencies", artifactIds.size(),
                    "springBootVersion", springBootVersion,
                    "detectedFrameworks", frameworks,
                    "hasTestDependencies", artifactIds.contains("spring-boot-starter-test") || artifactIds.contains("junit-jupiter"),
                    "hasMigrationTool", artifactIds.contains("flyway-core") || artifactIds.contains("liquibase-core")
            ));

            log.debug("Parsed pom.xml: Spring Boot {}, {} deps, frameworks: {}",
                    springBootVersion, artifactIds.size(), frameworks);

        } catch (Exception e) {
            log.warn("Failed to parse pom.xml for {}/{}: {}", owner, repo, e.getMessage());
        }
    }

    private void parseRequirementsTxt(String owner, String repo, String token, Map<String, Object> out, Set<String> paths) {
        try {
            String fileName = paths.contains("pyproject.toml") ? "pyproject.toml" : "requirements.txt";
            String content = fetchFileContent(owner, repo, fileName, token);
            if (content == null) content = fetchFileContent(owner, repo, "requirements.txt", token);
            if (content == null) return;

            List<String> packages = Arrays.stream(content.split("\n"))
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#") && !l.startsWith("-r"))
                    .map(l -> l.split("[>=<!~\\[;]")[0].trim().toLowerCase())
                    .filter(l -> !l.isEmpty())
                    .collect(Collectors.toList());

            List<String> frameworks = new ArrayList<>();
            Map<String, String> knownPython = new LinkedHashMap<>();
            knownPython.put("django", "Django");
            knownPython.put("flask", "Flask");
            knownPython.put("fastapi", "FastAPI");
            knownPython.put("starlette", "Starlette");
            knownPython.put("uvicorn", "Uvicorn");
            knownPython.put("gunicorn", "Gunicorn");
            knownPython.put("celery", "Celery");
            knownPython.put("sqlalchemy", "SQLAlchemy");
            knownPython.put("alembic", "Alembic (DB migrations)");
            knownPython.put("pydantic", "Pydantic");
            knownPython.put("pandas", "Pandas");
            knownPython.put("numpy", "NumPy");
            knownPython.put("scikit-learn", "scikit-learn");
            knownPython.put("torch", "PyTorch");
            knownPython.put("tensorflow", "TensorFlow");
            knownPython.put("transformers", "HuggingFace Transformers");
            knownPython.put("openai", "OpenAI SDK");
            knownPython.put("anthropic", "Anthropic SDK");
            knownPython.put("redis", "Redis");
            knownPython.put("pytest", "pytest");
            knownPython.put("httpx", "httpx");
            knownPython.put("requests", "requests");

            Set<String> packageSet = new HashSet<>(packages);
            for (var entry : knownPython.entrySet()) {
                if (packageSet.contains(entry.getKey())) frameworks.add(entry.getValue());
            }

            boolean isMlProject = packageSet.stream().anyMatch(p ->
                    p.equals("pandas") || p.equals("numpy") || p.equals("torch") ||
                    p.equals("tensorflow") || p.equals("scikit-learn") || p.equals("transformers"));

            out.put("python", Map.of(
                    "totalPackages", packages.size(),
                    "detectedFrameworks", frameworks,
                    "isMlProject", isMlProject,
                    "hasTestFramework", packageSet.contains("pytest"),
                    "hasAsyncSupport", packageSet.contains("uvicorn") || packageSet.contains("asyncio")
            ));

            log.debug("Parsed requirements: {} packages, frameworks: {}", packages.size(), frameworks);

        } catch (Exception e) {
            log.warn("Failed to parse requirements for {}/{}: {}", owner, repo, e.getMessage());
        }
    }

    private void parseGradleBuild(String owner, String repo, String token, Map<String, Object> out, Set<String> paths) {
        try {
            String fileName = paths.contains("build.gradle.kts") ? "build.gradle.kts" : "build.gradle";
            String content = fetchFileContent(owner, repo, fileName, token);
            if (content == null) return;

            List<String> frameworks = new ArrayList<>();
            Map<String, String> knownGradle = new LinkedHashMap<>();
            knownGradle.put("spring-boot-starter-web", "Spring Web MVC");
            knownGradle.put("spring-boot-starter-data-jpa", "Spring Data JPA");
            knownGradle.put("spring-boot-starter-security", "Spring Security");
            knownGradle.put("flyway-core", "Flyway");
            knownGradle.put("postgresql", "PostgreSQL");
            knownGradle.put("lombok", "Lombok");
            knownGradle.put("spring-boot-starter-test", "JUnit 5 / Spring Boot Test");
            knownGradle.put("testcontainers", "Testcontainers");
            knownGradle.put("kotlin-stdlib", "Kotlin");

            for (var entry : knownGradle.entrySet()) {
                if (content.contains(entry.getKey())) frameworks.add(entry.getValue());
            }

            // Count dependency lines
            long depCount = Arrays.stream(content.split("\n"))
                    .filter(l -> l.trim().startsWith("implementation") || l.trim().startsWith("testImplementation") || l.trim().startsWith("api "))
                    .count();

            out.put("gradle", Map.of(
                    "totalDependencies", depCount,
                    "detectedFrameworks", frameworks,
                    "isKotlinDsl", fileName.endsWith(".kts")
            ));

        } catch (Exception e) {
            log.warn("Failed to parse build.gradle for {}/{}: {}", owner, repo, e.getMessage());
        }
    }

    private void parseDockerfile(String owner, String repo, String token, Map<String, Object> out) {
        try {
            String content = fetchFileContent(owner, repo, "Dockerfile", token);
            if (content == null) return;

            List<String> fromStatements = Arrays.stream(content.split("\n"))
                    .map(String::trim)
                    .filter(l -> l.toUpperCase().startsWith("FROM"))
                    .collect(Collectors.toList());

            boolean isMultiStage = fromStatements.size() > 1;
            List<String> baseImages = fromStatements.stream()
                    .map(l -> l.substring(5).trim().split("\\s")[0]) // strip "FROM " and AS alias
                    .collect(Collectors.toList());

            // Detect exposed ports
            List<String> exposedPorts = Arrays.stream(content.split("\n"))
                    .map(String::trim)
                    .filter(l -> l.toUpperCase().startsWith("EXPOSE"))
                    .map(l -> l.substring(7).trim())
                    .collect(Collectors.toList());

            out.put("docker", Map.of(
                    "stageCount", fromStatements.size(),
                    "isMultiStage", isMultiStage,
                    "baseImages", baseImages,
                    "exposedPorts", exposedPorts
            ));

            log.debug("Parsed Dockerfile: {} stages, multi-stage={}, bases={}",
                    fromStatements.size(), isMultiStage, baseImages);

        } catch (Exception e) {
            log.warn("Failed to parse Dockerfile for {}/{}: {}", owner, repo, e.getMessage());
        }
    }

    private void parseGithubActions(String owner, String repo, String token,
                                    Map<String, Object> out, List<Map<String, Object>> tree) {
        try {
            // Find all workflow files
            List<String> workflowFiles = tree.stream()
                    .map(n -> (String) n.getOrDefault("path", ""))
                    .filter(p -> p.startsWith(".github/workflows/") && (p.endsWith(".yml") || p.endsWith(".yaml")))
                    .collect(Collectors.toList());

            if (workflowFiles.isEmpty()) return;

            List<String> workflowNames = new ArrayList<>();
            List<String> deployTargets = new ArrayList<>();
            boolean hasBuildJob = false, hasTestJob = false, hasDeployJob = false, hasPrChecks = false;

            for (String wfPath : workflowFiles) {
                String content = fetchFileContent(owner, repo, wfPath, token);
                if (content == null) continue;

                String lower = content.toLowerCase();
                workflowNames.add(wfPath.replace(".github/workflows/", "").replace(".yml", "").replace(".yaml", ""));

                if (lower.contains("build")) hasBuildJob = true;
                if (lower.contains("test") || lower.contains("junit") || lower.contains("pytest")) hasTestJob = true;
                if (lower.contains("deploy") || lower.contains("release")) hasDeployJob = true;
                if (lower.contains("pull_request")) hasPrChecks = true;

                // Detect deployment targets
                if (lower.contains("aws") || lower.contains("ecr") || lower.contains("ecs") || lower.contains("lambda")) deployTargets.add("AWS");
                if (lower.contains("gcp") || lower.contains("gke") || lower.contains("gcr") || lower.contains("cloud run")) deployTargets.add("GCP");
                if (lower.contains("azure")) deployTargets.add("Azure");
                if (lower.contains("vercel")) deployTargets.add("Vercel");
                if (lower.contains("heroku")) deployTargets.add("Heroku");
                if (lower.contains("docker") && (lower.contains("push") || lower.contains("registry"))) deployTargets.add("Docker Registry");
            }

            out.put("githubActions", Map.of(
                    "workflowCount", workflowFiles.size(),
                    "workflowNames", workflowNames,
                    "hasBuildJob", hasBuildJob,
                    "hasTestJob", hasTestJob,
                    "hasDeployJob", hasDeployJob,
                    "hasPrChecks", hasPrChecks,
                    "deployTargets", deployTargets.stream().distinct().collect(Collectors.toList())
            ));

            log.debug("Parsed {} GitHub Actions workflows, deployTargets={}", workflowFiles.size(), deployTargets);

        } catch (Exception e) {
            log.warn("Failed to parse GitHub Actions for {}/{}: {}", owner, repo, e.getMessage());
        }
    }

    // ─── Quantitative metrics ─────────────────────────────────────────────────

    private Map<String, Object> fetchQuantitativeMetrics(
            String owner, String repoName, String token, List<Map<String, Object>> tree) {

        Map<String, Object> metrics = new LinkedHashMap<>();

        // Test file count from tree
        long testFileCount = tree.stream()
                .map(n -> ((String) n.getOrDefault("path", "")).toLowerCase())
                .filter(p -> p.contains("test") || p.contains("spec") || p.contains("__tests__"))
                .filter(p -> !p.contains("node_modules"))
                .count();
        metrics.put("testFileCount", testFileCount);

        // Total file count
        long fileCount = tree.stream()
                .filter(n -> "blob".equals(n.get("type")))
                .count();
        metrics.put("totalFiles", fileCount);

        // Directory depth (max nesting level)
        int maxDepth = tree.stream()
                .map(n -> (String) n.getOrDefault("path", ""))
                .mapToInt(p -> p.split("/").length)
                .max().orElse(1);
        metrics.put("maxDirectoryDepth", maxDepth);

        // Commit count via GitHub API (Link header pagination trick)
        metrics.put("commitCount", fetchCommitCount(owner, repoName, token));

        // Contributor count
        metrics.put("contributorCount", fetchContributorCount(owner, repoName, token));

        // Language breakdown in bytes
        Map<String, Object> langBytes = fetchLanguageBreakdown(owner, repoName, token);
        metrics.put("languageBytes", langBytes);

        // Estimated total lines of code (~40 bytes per line average)
        long totalBytes = langBytes.values().stream()
                .mapToLong(v -> v instanceof Number ? ((Number) v).longValue() : 0)
                .sum();
        metrics.put("estimatedLinesOfCode", totalBytes / 40);

        log.debug("Quantitative metrics for {}/{}: {} commits, {} contributors, {} files, {} test files",
                owner, repoName,
                metrics.get("commitCount"), metrics.get("contributorCount"),
                fileCount, testFileCount);

        return metrics;
    }

    private int fetchCommitCount(String owner, String repo, String token) {
        try {
            // Request just 1 commit per page and read the last page number from the Link header
            String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/commits?per_page=1";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authHeaders(token),
                    new ParameterizedTypeReference<>() {}
            );
            String linkHeader = response.getHeaders().getFirst("Link");
            if (linkHeader != null && linkHeader.contains("rel=\"last\"")) {
                // Parse: <https://api.github.com/...?page=47>; rel="last"
                var matcher = java.util.regex.Pattern
                        .compile("[?&]page=(\\d+)>; rel=\"last\"")
                        .matcher(linkHeader);
                if (matcher.find()) return Integer.parseInt(matcher.group(1));
            }
            // No Link header means all commits fit in one page
            if (response.getBody() != null) return response.getBody().size();
        } catch (Exception e) {
            log.debug("Failed to fetch commit count for {}/{}: {}", owner, repo, e.getMessage());
        }
        return 0;
    }

    private int fetchContributorCount(String owner, String repo, String token) {
        try {
            String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contributors?per_page=1&anon=false";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authHeaders(token),
                    new ParameterizedTypeReference<>() {}
            );
            String linkHeader = response.getHeaders().getFirst("Link");
            if (linkHeader != null && linkHeader.contains("rel=\"last\"")) {
                var matcher = java.util.regex.Pattern
                        .compile("[?&]page=(\\d+)>; rel=\"last\"")
                        .matcher(linkHeader);
                if (matcher.find()) return Integer.parseInt(matcher.group(1));
            }
            if (response.getBody() != null) return response.getBody().size();
        } catch (Exception e) {
            log.debug("Failed to fetch contributor count for {}/{}: {}", owner, repo, e.getMessage());
        }
        return 0;
    }

    private Map<String, Object> fetchLanguageBreakdown(String owner, String repo, String token) {
        try {
            String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/languages";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authHeaders(token),
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            log.debug("Failed to fetch language breakdown for {}/{}: {}", owner, repo, e.getMessage());
        }
        return Map.of();
    }

    // ─── Stack builder ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> buildStack(Repository repo, Map<String, Object> signals, Map<String, Object> deps) {
        Set<String> stack = new LinkedHashSet<>();

        // Primary language
        if (repo.getPrimaryLanguage() != null) stack.add(repo.getPrimaryLanguage());

        // From NPM parsed deps - exact framework names
        if (deps.containsKey("npm")) {
            Map<String, Object> npm = (Map<String, Object>) deps.get("npm");
            List<String> frameworks = (List<String>) npm.getOrDefault("detectedFrameworks", List.of());
            // Add clean names without version for stack list
            frameworks.forEach(f -> stack.add(f.split(" ")[0] + (f.contains(" ") ? " " + f.split(" ")[1] : "")));
            if (Boolean.TRUE.equals(npm.get("hasTypeScript"))) stack.add("TypeScript");
        }

        // From Maven
        if (deps.containsKey("maven")) {
            Map<String, Object> maven = (Map<String, Object>) deps.get("maven");
            List<String> frameworks = (List<String>) maven.getOrDefault("detectedFrameworks", List.of());
            frameworks.forEach(stack::add);
            String sbVersion = (String) maven.getOrDefault("springBootVersion", "");
            if (!sbVersion.isEmpty()) {
                stack.remove("Spring Web MVC"); // Will be added back with Spring Boot label
                stack.add("Spring Boot " + sbVersion);
            }
        }

        // From Gradle
        if (deps.containsKey("gradle")) {
            Map<String, Object> gradle = (Map<String, Object>) deps.get("gradle");
            List<String> frameworks = (List<String>) gradle.getOrDefault("detectedFrameworks", List.of());
            frameworks.forEach(stack::add);
        }

        // From Python
        if (deps.containsKey("python")) {
            Map<String, Object> python = (Map<String, Object>) deps.get("python");
            List<String> frameworks = (List<String>) python.getOrDefault("detectedFrameworks", List.of());
            frameworks.forEach(stack::add);
        }

        // Infrastructure
        if (Boolean.TRUE.equals(signals.get("hasDocker"))) stack.add("Docker");
        if (Boolean.TRUE.equals(signals.get("hasDockerCompose"))) stack.add("Docker Compose");
        if (Boolean.TRUE.equals(signals.get("hasCI"))) stack.add("GitHub Actions");
        if (Boolean.TRUE.equals(signals.get("hasKubernetes"))) stack.add("Kubernetes");
        if (Boolean.TRUE.equals(signals.get("hasTerraform"))) stack.add("Terraform");

        // Topics
        if (repo.getTopics() != null) {
            Set<String> knownTech = Set.of(
                    "react", "vue", "angular", "svelte", "nextjs", "express", "fastapi",
                    "django", "flask", "spring", "springboot", "postgres", "postgresql",
                    "mysql", "mongodb", "redis", "graphql", "rest", "grpc", "kubernetes",
                    "terraform", "aws", "gcp", "azure", "kafka", "rabbitmq", "elasticsearch"
            );
            for (String topic : repo.getTopics()) {
                if (knownTech.contains(topic.toLowerCase())) {
                    stack.add(capitalize(topic));
                }
            }
        }

        // Front-end config signals (fallback if not detected via package.json)
        if (!deps.containsKey("npm")) {
            if (Boolean.TRUE.equals(signals.get("hasNextConfig"))) stack.add("Next.js");
            if (Boolean.TRUE.equals(signals.get("hasTailwind"))) stack.add("Tailwind CSS");
            if (Boolean.TRUE.equals(signals.get("hasVite"))) stack.add("Vite");
        }

        // Build tools
        if (Boolean.TRUE.equals(signals.get("hasPomXml")) && !deps.containsKey("maven")) stack.add("Maven");
        if (Boolean.TRUE.equals(signals.get("hasGradleBuild")) && !deps.containsKey("gradle")) stack.add("Gradle");

        return new ArrayList<>(stack);
    }

    // ─── Project type classifier ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String classifyProjectType(Repository repo, Map<String, Object> signals,
                                       Map<String, Object> deps, List<String> stack) {

        boolean hasFrontend = deps.containsKey("npm") || Boolean.TRUE.equals(signals.get("hasPackageJson"));
        boolean hasJavaBackend = deps.containsKey("maven") || deps.containsKey("gradle") ||
                Boolean.TRUE.equals(signals.get("hasPomXml")) || Boolean.TRUE.equals(signals.get("hasGradleBuild"));
        boolean hasPythonBackend = deps.containsKey("python") || Boolean.TRUE.equals(signals.get("hasRequirements"));
        boolean hasGoBackend = Boolean.TRUE.equals(signals.get("hasGoMod"));
        boolean hasRust = Boolean.TRUE.equals(signals.get("hasCargoToml"));
        boolean hasInfrastructure = Boolean.TRUE.equals(signals.get("hasDocker")) && Boolean.TRUE.equals(signals.get("hasCI"));

        // ML/AI detection
        if (deps.containsKey("python")) {
            Map<String, Object> python = (Map<String, Object>) deps.get("python");
            if (Boolean.TRUE.equals(python.get("isMlProject"))) return "ML/AI Project";
        }
        if (repo.getTopics() != null && repo.getTopics().stream()
                .anyMatch(t -> t.equalsIgnoreCase("machine-learning") || t.equalsIgnoreCase("deep-learning")
                        || t.equalsIgnoreCase("nlp") || t.equalsIgnoreCase("ai"))) {
            return "ML/AI Project";
        }

        // Full stack: frontend + backend
        if (hasFrontend && (hasJavaBackend || hasPythonBackend || hasGoBackend)) {
            return "Full Stack Web Application";
        }

        // DevOps / Infrastructure
        if (hasInfrastructure && Boolean.TRUE.equals(signals.get("hasTerraform"))) return "DevOps/Infrastructure";
        if (hasInfrastructure && Boolean.TRUE.equals(signals.get("hasKubernetes"))) return "DevOps/Infrastructure";

        // Backend API
        if ((hasJavaBackend || hasPythonBackend || hasGoBackend) && !hasFrontend) {
            if (Boolean.TRUE.equals(signals.get("hasControllers")) || Boolean.TRUE.equals(signals.get("hasOpenApi"))) {
                return "Backend REST API";
            }
            return "Backend Application";
        }

        // Frontend SPA
        if (hasFrontend && !hasJavaBackend && !hasPythonBackend && !hasGoBackend) {
            return "Frontend SPA";
        }

        // CLI / Systems
        if (hasRust && !hasFrontend) return "CLI Tool / Systems";
        if (hasGoBackend && !hasFrontend) return "CLI Tool / Backend Service";

        // Library heuristic
        String nameLower = repo.getName() != null ? repo.getName().toLowerCase() : "";
        String descLower = repo.getDescription() != null ? repo.getDescription().toLowerCase() : "";
        if (nameLower.contains("sdk") || nameLower.contains("lib") || nameLower.contains("client") ||
                descLower.contains("library") || descLower.contains("sdk")) {
            return "Library/SDK";
        }

        return "Software Project";
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String fetchFileContent(String owner, String repo, String path, String token) {
        try {
            String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + path;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authHeaders(token),
                    new ParameterizedTypeReference<>() {}
            );
            if (response.getBody() != null && response.getBody().containsKey("content")) {
                String encoded = (String) response.getBody().get("content");
                byte[] decoded = Base64.getMimeDecoder().decode(encoded);
                return new String(decoded);
            }
        } catch (HttpClientErrorException.NotFound e) {
            // File doesn't exist, silently ignore
        } catch (Exception e) {
            log.debug("Failed to fetch {}/{}/{}: {}", owner, repo, path, e.getMessage());
        }
        return null;
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
