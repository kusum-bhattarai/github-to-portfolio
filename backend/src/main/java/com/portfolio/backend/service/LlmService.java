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
        appendMetric(metricsSection, "Total source files", metrics.get("totalFiles"));
        appendMetric(metricsSection, "Test files", metrics.get("testFileCount"));
        appendMetric(metricsSection, "Estimated lines of code", metrics.get("estimatedLinesOfCode"));
        appendMetric(metricsSection, "Total commits", metrics.get("commitCount"));
        appendMetric(metricsSection, "Contributors", metrics.get("contributorCount"));
        appendMetric(metricsSection, "Stars / Forks", repo.getStars() + " / " + repo.getForks());
        appendMetric(metricsSection, "Max directory depth", metrics.get("maxDirectoryDepth"));

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
            @SuppressWarnings("unchecked")
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
                You are a senior software engineer at a top tech company helping a developer craft
                exceptional portfolio content. Your goal is to produce resume bullets and summaries
                that would genuinely impress a FAANG hiring manager or senior engineer.

                REPOSITORY: %s
                Description: %s
                Detected stack: %s

                ═══════════════════════════════════════════
                QUANTITATIVE METRICS (use these in bullets):
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

                STRICT RULES FOR RESUME BULLETS:
                - Write EXACTLY 4 bullets
                - Start each with a strong action verb: Architected, Engineered, Implemented, Designed, Built, Developed, Optimized, Reduced, Increased, Automated
                - ALWAYS include at least 2 concrete numbers from the metrics above (commit count, contributor count, LOC, dependency count, file count, test count, etc.)
                - Mention the most impressive technical decisions: multi-stage Docker builds, CI/CD stages, DB migration tooling, architectural patterns, specific framework versions
                - Write at the level of a new grad at a top tech company — specific, non-generic, technically rigorous
                - Bad example: "Built a web application with React and Spring Boot"
                - Good example: "Engineered a full-stack portfolio analytics platform across 3 architectural layers (controller/service/repository) with Spring Boot 3.2, reducing manual portfolio effort by automating content generation for %d+ repositories"

                RULES FOR portfolioSummary:
                - 2-3 sentences, professional tone
                - Mention the project type (%s), the core technical challenge solved, and 1-2 scale/scope indicators from the metrics
                - Do not use the word "leveraging" or "utilizing"

                RULES FOR techStack:
                - Only include technologies confirmed by dependency analysis above — no hallucination
                - Use canonical names with versions where available (e.g., "Spring Boot 3.2", "React 18")

                RULES FOR projectTags:
                - Pick 2-3 from: Full Stack, Backend, Frontend, DevOps, ML/AI, CLI Tool, API, Mobile, Library
                - Must match the project type: %s
                """.formatted(
                repo.getName(),
                repo.getDescription() != null ? repo.getDescription() : "No description provided",
                String.join(", ", evidence.detectedStack()),
                metricsSection.toString().trim(),
                depsSection.toString().trim(),
                infraSection.toString().trim(),
                readmeSnippet,
                metrics.getOrDefault("commitCount", 0),
                evidence.projectType(),
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
