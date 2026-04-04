package com.portfolio.backend.service;

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

import java.util.List;
import java.util.Map;

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
                .maxCompletionTokens(1000)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);
        String content = completion.choices().get(0).message().content().orElse("{}");

        log.debug("LLM response for {}: {}", repo.getFullName(), content);
        return parseResponse(content);
    }

    private String buildPrompt(Repository repo, EvidenceExtractor.ExtractionResult evidence) {
        String signalsSummary = buildSignalsSummary(evidence.signals());
        String readmeSnippet = evidence.readmeContent().isEmpty()
                ? "No README available."
                : evidence.readmeContent().substring(0, Math.min(evidence.readmeContent().length(), 1500));

        return """
                You are a professional software engineer helping a developer create portfolio content.
                Analyze this GitHub repository and generate professional content.

                REPOSITORY INFORMATION:
                - Name: %s
                - Description: %s
                - Primary Language: %s
                - Stars: %d | Forks: %d
                - Topics: %s
                - Detected Stack: %s

                TECHNICAL SIGNALS:
                %s

                README (truncated):
                %s

                Generate a JSON response with exactly these fields:
                {
                  "portfolioSummary": "2-3 sentence professional description of the project and its technical impact",
                  "resumeBullets": ["bullet 1 starting with action verb", "bullet 2", "bullet 3"],
                  "techStack": ["Tech1", "Tech2", "Tech3"],
                  "projectTags": ["Tag1", "Tag2"]
                }

                Rules:
                - portfolioSummary: professional, specific, no generic fluff
                - resumeBullets: 3-4 bullets, start with strong action verbs (Built, Designed, Implemented, Engineered), be specific about what was built
                - techStack: only include technologies that are evident from the signals, no hallucination
                - projectTags: 2-3 tags from: Full Stack, Backend, Frontend, DevOps, ML/AI, CLI Tool, API, Mobile, Library
                """.formatted(
                repo.getName(),
                repo.getDescription() != null ? repo.getDescription() : "No description",
                repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : "Unknown",
                repo.getStars(),
                repo.getForks(),
                repo.getTopics() != null ? String.join(", ", repo.getTopics()) : "none",
                String.join(", ", evidence.detectedStack()),
                signalsSummary,
                readmeSnippet
        );
    }

    private String buildSignalsSummary(Map<String, Object> signals) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> labels = Map.of(
                "hasDocker", "Has Docker/containerization",
                "hasCI", "Has CI/CD (GitHub Actions)",
                "hasTests", "Has test files",
                "hasMigrations", "Has database migrations",
                "hasControllers", "Has MVC controller structure",
                "hasServices", "Has service layer",
                "hasEnvExample", "Has environment config"
        );
        for (var entry : labels.entrySet()) {
            if (Boolean.TRUE.equals(signals.get(entry.getKey()))) {
                sb.append("- ").append(entry.getValue()).append("\n");
            }
        }
        return sb.isEmpty() ? "- No specific signals detected" : sb.toString();
    }

    private GeneratedPortfolioContent parseResponse(String json) {
        try {
            var node = objectMapper.readTree(json);
            String summary = node.path("portfolioSummary").asText("");
            List<String> bullets = objectMapper.convertValue(node.path("resumeBullets"), List.class);
            List<String> stack = objectMapper.convertValue(node.path("techStack"), List.class);
            List<String> tags = objectMapper.convertValue(node.path("projectTags"), List.class);
            return new GeneratedPortfolioContent(summary, bullets, stack, tags);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", json, e);
            return new GeneratedPortfolioContent("Failed to generate content.", List.of(), List.of(), List.of());
        }
    }
}
