package com.portfolio.backend.kafka.consumer;

import com.portfolio.backend.entity.*;
import com.portfolio.backend.kafka.KafkaAnalysisPublisher;
import com.portfolio.backend.kafka.KafkaTopics;
import com.portfolio.backend.kafka.event.AnalysisRequestedEvent;
import com.portfolio.backend.kafka.event.EvidencePayload;
import com.portfolio.backend.repository.*;
import com.portfolio.backend.service.EvidenceExtractor;
import com.portfolio.backend.service.JobStateService;
import com.portfolio.backend.service.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stage 1: Evidence Extraction
 *
 * Consumes {@code repo.analysis.requested}, fetches GitHub data, builds the structured
 * RepoSnapshot, persists it, then publishes {@code repo.evidence.extracted} for Stage 2.
 *
 * On failure: publishes to {@code repo.generation.failed} (DLQ) for retry/final disposition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionStageConsumer {

    private static final String GROUP = "portfolio-extraction";

    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final RepoSnapshotRepository snapshotRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final EvidenceExtractor evidenceExtractor;
    private final JobStateService jobStateService;
    private final KafkaAnalysisPublisher publisher;

    @KafkaListener(topics = KafkaTopics.ANALYSIS_REQUESTED, groupId = GROUP,
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onAnalysisRequested(AnalysisRequestedEvent event) {
        log.info("ExtractionStage received — jobId={} repoId={} attempt={}",
                event.jobId(), event.repositoryId(), event.attempt());

        jobStateService.transition(event.jobId(), JobStatus.PROCESSING);

        try {
            Repository repo = repositoryRepository.findById(event.repositoryId())
                    .orElseThrow(() -> new IllegalStateException("Repository not found: " + event.repositoryId()));
            User user = userRepository.findById(event.userId())
                    .orElseThrow(() -> new IllegalStateException("User not found: " + event.userId()));

            GitHubConnection connection = connectionRepository.findByUser(user)
                    .orElseThrow(() -> new IllegalStateException("No GitHub connection for user: " + event.userId()));
            String token = tokenEncryptionService.decrypt(connection.getEncryptedAccessToken());

            EvidenceExtractor.ExtractionResult result = evidenceExtractor.extract(repo, token);

            // Persist snapshot so it's durable even if downstream stages fail
            RepoSnapshot snapshot = snapshotRepository.findByRepository(repo)
                    .orElseGet(() -> RepoSnapshot.builder().repository(repo).build());
            snapshot.setReadmeContent(result.readmeContent());
            snapshot.setDetectedStack(result.detectedStack());
            Map<String, Object> mergedSignals = new LinkedHashMap<>(result.signals());
            if (!result.commitSignals().isEmpty()) {
                mergedSignals.put("commitSignals", result.commitSignals());
            }
            snapshot.setExtractedSignals(mergedSignals);
            snapshot.setProjectType(result.projectType());
            snapshot.setParsedDependencies(result.parsedDependencies());
            snapshot.setQuantitativeMetrics(result.quantitativeMetrics());
            snapshotRepository.save(snapshot);

            publisher.publishEvidenceExtracted(
                    event.jobId(), event.repositoryId(), event.userId(),
                    event.attempt(), EvidencePayload.from(result));

            log.info("ExtractionStage complete — jobId={}", event.jobId());

        } catch (Exception e) {
            log.error("ExtractionStage failed — jobId={}: {}", event.jobId(), e.getMessage(), e);
            publisher.publishGenerationFailed(
                    event.jobId(), event.repositoryId(), event.userId(),
                    "extraction", truncate(e.getMessage()), event.attempt());
        }
    }

    private String truncate(String msg) {
        if (msg == null) return "Unknown error";
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
