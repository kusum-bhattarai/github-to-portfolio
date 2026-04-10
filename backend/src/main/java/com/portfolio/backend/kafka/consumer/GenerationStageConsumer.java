package com.portfolio.backend.kafka.consumer;

import com.portfolio.backend.entity.Repository;
import com.portfolio.backend.kafka.KafkaAnalysisPublisher;
import com.portfolio.backend.kafka.KafkaTopics;
import com.portfolio.backend.kafka.event.EvidenceExtractedEvent;
import com.portfolio.backend.kafka.event.GenerationCompletedEvent;
import com.portfolio.backend.repository.RepositoryRepository;
import com.portfolio.backend.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Stage 2: LLM Generation
 *
 * Consumes {@code repo.evidence.extracted}, calls the LLM with the structured evidence,
 * and publishes the generated content to {@code repo.generation.completed}.
 *
 * This consumer is stateless — all inputs come from the event payload.
 * On failure: routes to {@code repo.generation.failed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationStageConsumer {

    private static final String GROUP = "portfolio-generation";

    private final RepositoryRepository repositoryRepository;
    private final LlmService llmService;
    private final KafkaAnalysisPublisher publisher;

    @KafkaListener(topics = KafkaTopics.EVIDENCE_EXTRACTED, groupId = GROUP,
                   containerFactory = "kafkaListenerContainerFactory")
    public void onEvidenceExtracted(EvidenceExtractedEvent event) {
        log.info("GenerationStage received — jobId={} attempt={}", event.jobId(), event.attempt());

        try {
            Repository repo = repositoryRepository.findById(event.repositoryId())
                    .orElseThrow(() -> new IllegalStateException("Repository not found: " + event.repositoryId()));

            LlmService.GeneratedPortfolioContent generated =
                    llmService.generate(repo, event.evidence().toExtractionResult());

            GenerationCompletedEvent completed = new GenerationCompletedEvent(
                    event.jobId(),
                    event.repositoryId(),
                    generated.portfolioSummary(),
                    generated.resumeBullets(),
                    generated.techStack(),
                    generated.projectTags(),
                    generated.interviewStory(),
                    generated.oneLinePitch(),
                    generated.talkingPoints()
            );
            publisher.publishGenerationCompleted(completed);
            log.info("GenerationStage complete — jobId={}", event.jobId());

        } catch (Exception e) {
            log.error("GenerationStage failed — jobId={}: {}", event.jobId(), e.getMessage(), e);
            publisher.publishGenerationFailed(
                    event.jobId(), event.repositoryId(), event.userId(),
                    "generation", truncate(e.getMessage()), event.attempt());
        }
    }

    private String truncate(String msg) {
        if (msg == null) return "Unknown error";
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
