package com.portfolio.backend.service;

import com.portfolio.backend.entity.*;
import com.portfolio.backend.entity.GeneratedContent.Type;
import com.portfolio.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final RepositoryRepository repositoryRepository;
    private final RepoSnapshotRepository snapshotRepository;
    private final GeneratedContentRepository generatedContentRepository;
    private final EditedContentRepository editedContentRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final EvidenceExtractor evidenceExtractor;
    private final LlmService llmService;

    // ── Analysis ─────────────────────────────────────────────────────────────

    @Transactional
    public List<GeneratedContent> analyze(UUID repositoryId, User user) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        if (!repo.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Repository does not belong to user");
        }

        log.info("Starting analysis for repo: {}", repo.getFullName());

        GitHubConnection connection = connectionRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("No GitHub connection found"));
        String accessToken = tokenEncryptionService.decrypt(connection.getEncryptedAccessToken());

        EvidenceExtractor.ExtractionResult evidence = evidenceExtractor.extract(repo, accessToken);

        // Persist full Phase 4 snapshot
        RepoSnapshot snapshot = snapshotRepository.findByRepository(repo)
                .orElseGet(() -> RepoSnapshot.builder().repository(repo).build());
        snapshot.setReadmeContent(evidence.readmeContent());
        snapshot.setDetectedStack(evidence.detectedStack());
        snapshot.setExtractedSignals(evidence.signals());
        snapshot.setProjectType(evidence.projectType());
        snapshot.setParsedDependencies(evidence.parsedDependencies());
        snapshot.setQuantitativeMetrics(evidence.quantitativeMetrics());
        snapshotRepository.save(snapshot);

        log.info("Calling LLM for repo: {}", repo.getFullName());
        LlmService.GeneratedPortfolioContent generated = llmService.generate(repo, evidence);

        // Clear old generated content AND any edits (cascades via FK)
        generatedContentRepository.deleteByRepository(repo);

        List<GeneratedContent> results = List.of(
                buildContent(repo, Type.PORTFOLIO_SUMMARY, generated.portfolioSummary()),
                buildContent(repo, Type.RESUME_BULLETS, String.join("\n", generated.resumeBullets())),
                buildContent(repo, Type.TECH_STACK, String.join(", ", generated.techStack())),
                buildContent(repo, Type.PROJECT_TAGS, String.join(", ", generated.projectTags()))
        );

        generatedContentRepository.saveAll(results);
        log.info("Analysis complete for repo: {}", repo.getFullName());
        return results;
    }

    // ── Content retrieval ─────────────────────────────────────────────────────

    public List<GeneratedContent> getContent(UUID repositoryId, User user) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));
        if (!repo.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Repository does not belong to user");
        }
        return generatedContentRepository.findByRepositoryOrderByCreatedAtDesc(repo);
    }

    // ── Edit persistence ─────────────────────────────────────────────────────

    @Transactional
    public EditedContent saveEdit(UUID contentId, String newText, User user) {
        GeneratedContent gc = generatedContentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Content not found: " + contentId));

        if (!gc.getRepository().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Content does not belong to user");
        }

        EditedContent edit = editedContentRepository.findByGeneratedContent(gc)
                .orElseGet(() -> EditedContent.builder().generatedContent(gc).build());
        edit.setEditedText(newText);
        return editedContentRepository.save(edit);
    }

    // ── Workspace: all analyzed projects ─────────────────────────────────────

    public List<ProjectSummaryDto> getProjects(User user) {
        List<Repository> analyzedRepos = generatedContentRepository.findAnalyzedRepositoriesByUser(user);

        List<ProjectSummaryDto> result = new ArrayList<>();
        for (Repository repo : analyzedRepos) {
            RepoSnapshot snapshot = snapshotRepository.findByRepository(repo).orElse(null);
            List<GeneratedContent> content = generatedContentRepository.findByRepositoryOrderByCreatedAtDesc(repo);

            String portfolioSummary = content.stream()
                    .filter(c -> Type.PORTFOLIO_SUMMARY.name().equals(c.getContentType()))
                    .map(GeneratedContent::getGeneratedText)
                    .findFirst().orElse(null);

            String projectTags = content.stream()
                    .filter(c -> Type.PROJECT_TAGS.name().equals(c.getContentType()))
                    .map(GeneratedContent::getGeneratedText)
                    .findFirst().orElse(null);

            result.add(new ProjectSummaryDto(
                    repo.getId(),
                    repo.getName(),
                    repo.getFullName(),
                    repo.getDescription(),
                    repo.getPrimaryLanguage(),
                    repo.getStars(),
                    repo.getHtmlUrl(),
                    snapshot != null ? snapshot.getAnalyzedAt() : null,
                    snapshot != null ? snapshot.getProjectType() : null,
                    projectTags,
                    portfolioSummary
            ));
        }
        return result;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record ProjectSummaryDto(
            UUID repoId,
            String repoName,
            String repoFullName,
            String description,
            String primaryLanguage,
            int stars,
            String htmlUrl,
            OffsetDateTime analyzedAt,
            String projectType,
            String projectTags,
            String portfolioSummary
    ) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GeneratedContent buildContent(Repository repo, Type type, String text) {
        return GeneratedContent.builder()
                .repository(repo)
                .contentType(type.name())
                .generatedText(text)
                .build();
    }
}
