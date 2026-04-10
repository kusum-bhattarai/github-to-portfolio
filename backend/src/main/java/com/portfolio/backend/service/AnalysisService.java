package com.portfolio.backend.service;

import com.portfolio.backend.entity.*;
import com.portfolio.backend.entity.GeneratedContent.Type;
import com.portfolio.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final RepositoryRepository repositoryRepository;
    private final RepoSnapshotRepository snapshotRepository;
    private final GeneratedContentRepository generatedContentRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final EvidenceExtractor evidenceExtractor;
    private final LlmService llmService;

    @Transactional
    public List<GeneratedContent> analyze(UUID repositoryId, User user) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        if (!repo.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Repository does not belong to user");
        }

        log.info("Starting analysis for repo: {}", repo.getFullName());

        // Get access token
        GitHubConnection connection = connectionRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("No GitHub connection found"));
        String accessToken = tokenEncryptionService.decrypt(connection.getEncryptedAccessToken());

        // Extract evidence
        EvidenceExtractor.ExtractionResult evidence = evidenceExtractor.extract(repo, accessToken);

        // Persist snapshot
        RepoSnapshot snapshot = snapshotRepository.findByRepository(repo)
                .orElseGet(() -> RepoSnapshot.builder().repository(repo).build());
        snapshot.setReadmeContent(evidence.readmeContent());
        snapshot.setDetectedStack(evidence.detectedStack());
        snapshot.setExtractedSignals(evidence.signals());
        snapshotRepository.save(snapshot);

        // Call LLM
        log.info("Calling LLM for repo: {}", repo.getFullName());
        LlmService.GeneratedPortfolioContent generated = llmService.generate(repo, evidence);

        // Delete old generated content and save fresh
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

    public List<GeneratedContent> getContent(UUID repositoryId, User user) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));
        if (!repo.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Repository does not belong to user");
        }
        return generatedContentRepository.findByRepositoryOrderByCreatedAtDesc(repo);
    }

    private GeneratedContent buildContent(Repository repo, Type type, String text) {
        return GeneratedContent.builder()
                .repository(repo)
                .contentType(type.name())
                .generatedText(text)
                .build();
    }
}
