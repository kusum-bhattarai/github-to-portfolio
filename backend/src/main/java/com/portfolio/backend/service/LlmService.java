package com.portfolio.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.*;
import com.portfolio.backend.entity.Repository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmService {

    @Value("${app.llm.api-key}")
    private String apiKey;

    private OpenAIClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public record GeneratedPortfolioContent(
            String portfolioSummary,
            List<String> resumeBullets,
            List<String> techStack,
            List<String> projectTags
    ) {}

    public GeneratedPortfolioContent generate(Repository repo, EvidenceExtractor.ExtractionResult evidence) {
        String prompt = buildPrompt(repo, evidence);

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("gpt-4o-mini")
                .addUserMessage(prompt)
                .responseFormat(ChatCompletionCreateParams.ResponseFormat.ofJsonObject(
                        ResponseFormatJsonObject.builder().build()
                ))
                .maxCompletionTokens(1500)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);
        String content = completion.choices().get(0).message().content().orElse("{}");

        log.debug("LLM response for {}: {}", repo.getFullName(), content);
        return parseResponse(content);
    }

    @SuppressWarnings("unchecked")
    private String buildPrompt(Repository repo, EvidenceExtractor.ExtractionResult evidence) {
        Map<String, Object> metrics = evidence.quantitativeMetrics();
        Map<String, Object> deps = evidence.parsedDependencies();

        // ── Quantitative metrics section ─────────────────────────────────────
        StringBuilder metricsSection = new StringBuilder();
        appendMetric(metricsSection, "Project type", evidence.projectType());
        appendMetric(metricsSection, "Entity / model classes", metrics.get("entityClassCount"));
        appendMetric(metricsSection, "Controller / handler classes", metrics.get("controllerClassCount"));
        appendMetric(metricsSection, "Service classes", metrics.get("serviceClassCount"));
        appendMetric(metricsSection, "Test classes", metrics.get("testClassCount"));
        appendMetric(metricsSection, "Total commits", metrics.get("commitCount"));
        appendMetric(metricsSection, "Contributors", metrics.get("contributorCount"));
        appendMetric(metricsSection, "Stars / Forks", repo.getStars() + " / " + repo.getForks());

        // README-scraped metrics — these are the most valuable for bullets
        Map<String, Object> readmeMetrics = (Map<String, Object>) metrics.getOrDefault("readmeMetrics", Map.of());
        appendMetric(metricsSection, "Test coverage", readmeMetrics.get("testCoveragePercent"));
        appendMetric(metricsSection, "Response time", readmeMetrics.get("responseTimeMs"));
        appendMetric(metricsSection, "Throughput", readmeMetrics.get("throughput"));
        appendMetric(metricsSection, "Scale", readmeMetrics.get("scaleIndicator"));
        appendMetric(metricsSection, "API endpoints", readmeMetrics.get("apiEndpointCount"));

        // Language breakdown percentages
        Object langBytesObj = metrics.get("languageBytes");
        if (langBytesObj instanceof Map<?, ?> langBytes && !langBytes.isEmpty()) {
            long total = langBytes.values().stream()
                    .mapToLong(v -> v instanceof Number ? ((Number) v).longValue() : 0).sum();
            String langBreakdown = langBytes.entrySet().stream()
                    .sorted((a, b) -> Long.compare(
                            ((Number) b.getValue()).longValue(),
                            ((Number) a.getValue()).longValue()))
                    .limit(4)
                    .map(e -> e.getKey() + " " + (total > 0 ? (((Number) e.getValue()).longValue() * 100 / total) + "%" : ""))
                    .collect(Collectors.joining(", "));
            appendMetric(metricsSection, "Language breakdown", langBreakdown);
        }

        // ── Dependency details section ────────────────────────────────────────
        StringBuilder depsSection = new StringBuilder();

        if (deps.containsKey("npm")) {
            Map<String, Object> npm = (Map<String, Object>) deps.get("npm");
            List<String> frameworks = (List<String>) npm.getOrDefault("detectedFrameworks", List.of());
            appendMetric(depsSection, "NPM dependencies", npm.get("productionDependencies") + " prod + " + npm.get("devDependencies") + " dev");
            if (!frameworks.isEmpty()) appendMetric(depsSection, "JS/TS frameworks", String.join(", ", frameworks));
            if (Boolean.TRUE.equals(npm.get("hasTestFramework"))) depsSection.append("- Has JavaScript test framework (Jest/Vitest/Mocha)\n");
            if (Boolean.TRUE.equals(npm.get("hasTypeScript"))) depsSection.append("- TypeScript configured\n");
        }

        if (deps.containsKey("maven")) {
            Map<String, Object> maven = (Map<String, Object>) deps.get("maven");
            List<String> frameworks = (List<String>) maven.getOrDefault("detectedFrameworks", List.of());
            String sbVersion = (String) maven.getOrDefault("springBootVersion", "");
            appendMetric(depsSection, "Maven dependencies", maven.get("totalDependencies"));
            if (!sbVersion.isEmpty()) appendMetric(depsSection, "Spring Boot version", sbVersion);
            if (!frameworks.isEmpty()) appendMetric(depsSection, "Java frameworks", String.join(", ", frameworks));
            if (Boolean.TRUE.equals(maven.get("hasTestDependencies"))) depsSection.append("- Has JUnit 5 / Spring Boot Test dependency\n");
            if (Boolean.TRUE.equals(maven.get("hasMigrationTool"))) depsSection.append("- Has database migration tool (Flyway/Liquibase)\n");
        }

        if (deps.containsKey("gradle")) {
            Map<String, Object> gradle = (Map<String, Object>) deps.get("gradle");
            List<String> frameworks = (List<String>) gradle.getOrDefault("detectedFrameworks", List.of());
            appendMetric(depsSection, "Gradle dependencies", gradle.get("totalDependencies"));
            if (!frameworks.isEmpty()) appendMetric(depsSection, "Gradle frameworks", String.join(", ", frameworks));
        }

        if (deps.containsKey("python")) {
            Map<String, Object> python = (Map<String, Object>) deps.get("python");
            List<String> frameworks = (List<String>) python.getOrDefault("detectedFrameworks", List.of());
            appendMetric(depsSection, "Python packages", python.get("totalPackages"));
            if (!frameworks.isEmpty()) appendMetric(depsSection, "Python frameworks", String.join(", ", frameworks));
            if (Boolean.TRUE.equals(python.get("hasTestFramework"))) depsSection.append("- Has pytest\n");
        }

        if (deps.containsKey("docker")) {
            Map<String, Object> docker = (Map<String, Object>) deps.get("docker");
            List<Object> baseImages = (List<Object>) docker.getOrDefault("baseImages", List.of());
            if (Boolean.TRUE.equals(docker.get("isMultiStage"))) {
                String imageList = baseImages.stream().limit(3).map(Object::toString).collect(Collectors.joining(", "));
                appendMetric(depsSection, "Docker", docker.get("stageCount") + "-stage multi-stage build, base images: " + imageList);
            } else {
                String firstImage = baseImages.stream().map(Object::toString).findFirst().orElse("unknown");
                appendMetric(depsSection, "Docker", "single-stage, base: " + firstImage);
            }
        }

        if (deps.containsKey("githubActions")) {
            Map<String, Object> ci = (Map<String, Object>) deps.get("githubActions");
            List<String> stages = new ArrayList<>();
            if (Boolean.TRUE.equals(ci.get("hasBuildJob"))) stages.add("build");
            if (Boolean.TRUE.equals(ci.get("hasTestJob"))) stages.add("test");
            if (Boolean.TRUE.equals(ci.get("hasDeployJob"))) stages.add("deploy");
            if (Boolean.TRUE.equals(ci.get("hasPrChecks"))) stages.add("PR checks");
            List<String> targets = (List<String>) ci.getOrDefault("deployTargets", List.of());
            appendMetric(depsSection, "CI/CD pipeline", ci.get("workflowCount") + " workflow(s), stages: "
                    + (stages.isEmpty() ? "configured" : String.join(", ", stages))
                    + (targets.isEmpty() ? "" : ", deploys to: " + String.join(", ", targets)));
        }

        // ── Commit history signals (Phase 7) ─────────────────────────────────
        StringBuilder commitSection = new StringBuilder();
        Map<String, Object> commitSignals = evidence.commitSignals();
        if (!commitSignals.isEmpty()) {
            Object messagesAnalyzed = commitSignals.get("messagesAnalyzed");
            @SuppressWarnings("unchecked")
            List<String> features = (List<String>) commitSignals.getOrDefault("detectedFeatures", List.of());
            @SuppressWarnings("unchecked")
            List<String> recentMsgs = (List<String>) commitSignals.getOrDefault("recentMessages", List.of());

            if (messagesAnalyzed != null) {
                commitSection.append("Commits analyzed: ").append(messagesAnalyzed).append("\n");
            }
            if (!features.isEmpty()) {
                commitSection.append("Features detected from commit history: ")
                        .append(String.join(", ", features)).append("\n");
            }
            if (!recentMsgs.isEmpty()) {
                commitSection.append("Sample recent commits:\n");
                recentMsgs.stream().limit(8).forEach(m -> commitSection.append("  • ").append(m).append("\n"));
            }
        }

        // ── Infrastructure signals ────────────────────────────────────────────
        StringBuilder infraSection = new StringBuilder();
        Map<String, String> infraLabels = new LinkedHashMap<>();
        infraLabels.put("hasDocker", "Containerized with Docker");
        infraLabels.put("hasDockerCompose", "Multi-container with Docker Compose");
        infraLabels.put("hasCI", "CI/CD pipeline (GitHub Actions)");
        infraLabels.put("hasMigrations", "Database schema migrations managed");
        infraLabels.put("hasControllers", "MVC controller layer");
        infraLabels.put("hasServices", "Service layer architecture");
        infraLabels.put("hasModels", "Data model / entity layer");
        infraLabels.put("hasRepositories", "Repository / DAO pattern");
        infraLabels.put("hasOpenApi", "OpenAPI / Swagger documentation");
        infraLabels.put("hasEnvExample", "Environment configuration documented");
        infraLabels.put("hasKubernetes", "Kubernetes deployment config");
        infraLabels.put("hasTerraform", "Infrastructure as Code (Terraform)");

        for (var entry : infraLabels.entrySet()) {
            if (Boolean.TRUE.equals(evidence.signals().get(entry.getKey()))) {
                infraSection.append("- ").append(entry.getValue()).append("\n");
            }
        }

        // ── README snippet ───────────────────────────────────────────────────
        String readmeSnippet = evidence.readmeContent().isEmpty()
                ? "No README available."
                : evidence.readmeContent().substring(0, Math.min(evidence.readmeContent().length(), 1200));

        return """
                You are a staff engineer at a top tech company writing resume bullets for a developer's portfolio.
                Your bullets will be read by FAANG recruiters and senior engineers who reject anything generic.

                REPOSITORY: %s
                Description: %s
                Detected stack: %s

                ═══════════════════════════════════════════
                CONCRETE METRICS — embed these in bullets:
                ═══════════════════════════════════════════
                %s
                ═══════════════════════════════════════════
                DEPENDENCY & TOOLING DETAILS:
                ═══════════════════════════════════════════
                %s
                ═══════════════════════════════════════════
                ARCHITECTURE SIGNALS:
                ═══════════════════════════════════════════
                %s
                ═══════════════════════════════════════════
                COMMIT HISTORY SIGNALS — what was actually built over time:
                ═══════════════════════════════════════════
                %s
                ═══════════════════════════════════════════
                README (truncated):
                ═══════════════════════════════════════════
                %s

                Generate a JSON response with exactly these fields:
                {
                  "portfolioSummary": "2-3 sentence professional description",
                  "resumeBullets": ["bullet 1", "bullet 2", "bullet 3", "bullet 4"],
                  "techStack": ["Tech1", "Tech2"],
                  "projectTags": ["Tag1", "Tag2"]
                }

                ═══════════════════════════════════════════
                RESUME BULLET RULES — read carefully:
                ═══════════════════════════════════════════

                Write EXACTLY 4 bullets using Google's XYZ format:
                  "Accomplished [X] as measured by [Y], by doing [Z]"
                  X = the outcome or capability delivered
                  Y = a concrete metric (classes, endpoints, coverage %%, commits, contributors, latency, throughput, or scale from README)
                  Z = the specific technical approach, pattern, or architecture decision

                STRONG action verbs (pick different ones per bullet):
                  Architected, Engineered, Designed, Implemented, Built, Automated, Reduced, Increased, Optimized

                WHAT to mention (use what's available from the metrics above):
                  - Entity/model class count → scope of data model
                  - Controller/service class count → architectural depth
                  - Test class count or coverage %% → quality signal
                  - Commit count → project maturity and iteration history
                  - Contributor count → collaboration (if > 1)
                  - Specific framework versions (Spring Boot 3.x, React 18, etc.)
                  - CI/CD pipeline stages, Docker multi-stage builds, DB migration tooling
                  - Performance/throughput/scale numbers scraped from README (these are gold — use them)
                  - Features confirmed by commit history (auth, caching, real-time, etc.) — these prove the work actually happened

                WHAT TO AVOID:
                  - File counts, line counts — these mean nothing to a senior engineer
                  - Vague scope ("a web application", "a REST API", "a backend system")
                  - Passive language ("was used to", "is capable of")
                  - The words "leveraging", "utilizing", "robust", "scalable", "efficient"

                EXAMPLES — bad vs. good:
                  BAD:  "Built a web app with React and Spring Boot that handles user authentication"
                  GOOD: "Engineered full-stack authentication flow across 4 Spring Security filter layers with OAuth2/GitHub SSO, eliminating manual credential management for all users"

                  BAD:  "Implemented a REST API with multiple endpoints"
                  GOOD: "Designed a 3-tier REST API (controller → service → repository) spanning 6 entity classes and 12 endpoints, with Flyway-managed schema versioning across 5 migrations"

                  BAD:  "Added tests to the project"
                  GOOD: "Achieved 87%% test coverage across 14 JUnit 5 test classes using Mockito, catching 3 integration regressions before production"

                ═══════════════════════════════════════════
                OTHER FIELD RULES:
                ═══════════════════════════════════════════

                portfolioSummary:
                  - 2-3 sentences, written for a technical recruiter at a top company
                  - State: what it does, who it's for, and one concrete architectural or scale fact
                  - Do NOT use "leveraging", "utilizing", "robust", "scalable", or "modern"

                techStack:
                  - Only technologies confirmed by the dependency analysis — no hallucination
                  - Include versions where detected (e.g., "Spring Boot 3.2", "React 18.x")

                projectTags:
                  - 2-3 from: Full Stack, Backend, Frontend, DevOps, ML/AI, CLI Tool, API, Mobile, Library
                  - Must match project type: %s
                """.formatted(
                repo.getName(),
                repo.getDescription() != null ? repo.getDescription() : "No description provided",
                String.join(", ", evidence.detectedStack()),
                metricsSection.toString().trim(),
                depsSection.toString().trim(),
                infraSection.toString().trim(),
                commitSection.toString().trim().isEmpty() ? "No commit data available." : commitSection.toString().trim(),
                readmeSnippet,
                evidence.projectType()
        );
    }

    private void appendMetric(StringBuilder sb, String label, Object value) {
        if (value != null && !value.toString().isEmpty() && !value.toString().equals("0")) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }

    private GeneratedPortfolioContent parseResponse(String json) {
        try {
            var node = objectMapper.readTree(json);
            TypeReference<List<String>> listType = new TypeReference<>() {};
            String summary = node.path("portfolioSummary").asText("");
            List<String> bullets = objectMapper.convertValue(node.path("resumeBullets"), listType);
            List<String> stack = objectMapper.convertValue(node.path("techStack"), listType);
            List<String> tags = objectMapper.convertValue(node.path("projectTags"), listType);
            return new GeneratedPortfolioContent(summary, bullets, stack, tags);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", json, e);
            return new GeneratedPortfolioContent("Failed to generate content.", List.of(), List.of(), List.of());
        }
    }
}
