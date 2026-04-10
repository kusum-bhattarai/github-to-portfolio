package com.portfolio.backend.kafka;

import com.portfolio.backend.kafka.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thin wrapper around {@link KafkaTemplate} that publishes pipeline events.
 *
 * Keyed on {@code repositoryId} so that all events for the same repo are ordered
 * within a partition, preserving pipeline causality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaAnalysisPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAnalysisRequested(UUID jobId, UUID repositoryId, UUID userId) {
        AnalysisRequestedEvent event = new AnalysisRequestedEvent(jobId, repositoryId, userId, 1);
        log.info("Publishing {} — jobId={} repoId={}", KafkaTopics.ANALYSIS_REQUESTED, jobId, repositoryId);
        kafkaTemplate.send(KafkaTopics.ANALYSIS_REQUESTED, repositoryId.toString(), event);
    }

    public void publishEvidenceExtracted(UUID jobId, UUID repositoryId, UUID userId,
                                         int attempt, EvidencePayload evidence) {
        EvidenceExtractedEvent event = new EvidenceExtractedEvent(jobId, repositoryId, userId, attempt, evidence);
        log.info("Publishing {} — jobId={}", KafkaTopics.EVIDENCE_EXTRACTED, jobId);
        kafkaTemplate.send(KafkaTopics.EVIDENCE_EXTRACTED, repositoryId.toString(), event);
    }

    public void publishGenerationCompleted(GenerationCompletedEvent event) {
        log.info("Publishing {} — jobId={}", KafkaTopics.GENERATION_COMPLETED, event.jobId());
        kafkaTemplate.send(KafkaTopics.GENERATION_COMPLETED, event.repositoryId().toString(), event);
    }

    public void publishGenerationFailed(UUID jobId, UUID repositoryId, UUID userId,
                                        String stage, String errorMessage, int attempt) {
        GenerationFailedEvent event = new GenerationFailedEvent(
                jobId, repositoryId, userId, stage, errorMessage, attempt);
        log.warn("Publishing {} — jobId={} stage={} attempt={}", KafkaTopics.GENERATION_FAILED, jobId, stage, attempt);
        kafkaTemplate.send(KafkaTopics.GENERATION_FAILED, repositoryId.toString(), event);
    }
}
