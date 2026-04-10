package com.portfolio.backend.kafka.consumer;

import com.portfolio.backend.entity.*;
import com.portfolio.backend.entity.GeneratedContent.Type;
import com.portfolio.backend.kafka.KafkaAnalysisPublisher;
import com.portfolio.backend.kafka.KafkaTopics;
import com.portfolio.backend.kafka.event.GenerationCompletedEvent;
import com.portfolio.backend.repository.GeneratedContentRepository;
import com.portfolio.backend.repository.RepositoryRepository;
import com.portfolio.backend.service.JobStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 3: Persistence
 *
 * Consumes {@code repo.generation.completed}, writes the generated content to the
 * database, and transitions the job to COMPLETED.
 *
 * Separating persistence as its own stage means the LLM call and DB write are
 * independently retriable — a transient DB failure won't re-invoke the LLM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistenceStageConsumer {

    private static final String GROUP = "portfolio-persistence";

    private final RepositoryRepository repositoryRepository;
    private final GeneratedContentRepository generatedContentRepository;
    private final JobStateService jobStateService;
    private final KafkaAnalysisPublisher publisher;

    @KafkaListener(topics = KafkaTopics.GENERATION_COMPLETED, groupId = GROUP,
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onGenerationCompleted(GenerationCompletedEvent event) {
        log.info("PersistenceStage received — jobId={}", event.jobId());

        try {
            Repository repo = repositoryRepository.findById(event.repositoryId())
                    .orElseThrow(() -> new IllegalStateException("Repository not found: " + event.repositoryId()));

            // Replace all previously generated content for this repo
            generatedContentRepository.deleteByRepository(repo);

            List<GeneratedContent> toSave = new ArrayList<>();
            toSave.add(build(repo, Type.PORTFOLIO_SUMMARY,  event.portfolioSummary()));
            toSave.add(build(repo, Type.RESUME_BULLETS,     String.join("\n", event.resumeBullets())));
            toSave.add(build(repo, Type.TECH_STACK,         String.join(", ", event.techStack())));
            toSave.add(build(repo, Type.PROJECT_TAGS,       String.join(", ", event.projectTags())));
            if (event.oneLinePitch() != null && !event.oneLinePitch().isBlank())
                toSave.add(build(repo, Type.ONE_SENTENCE_PITCH, event.oneLinePitch()));
            if (event.talkingPoints() != null && !event.talkingPoints().isEmpty())
                toSave.add(build(repo, Type.TALKING_POINTS, String.join("\n", event.talkingPoints())));
            if (event.interviewStory() != null && !event.interviewStory().isBlank())
                toSave.add(build(repo, Type.INTERVIEW_STORY, event.interviewStory()));

            generatedContentRepository.saveAll(toSave);
            jobStateService.transition(event.jobId(), JobStatus.COMPLETED);
            log.info("PersistenceStage complete — jobId={} contentItems={}", event.jobId(), toSave.size());

        } catch (Exception e) {
            log.error("PersistenceStage failed — jobId={}: {}", event.jobId(), e.getMessage(), e);
            // Re-route to DLQ — userId not available at this stage, pass null-safe placeholder
            publisher.publishGenerationFailed(
                    event.jobId(), event.repositoryId(), null,
                    "persistence", truncate(e.getMessage()), 0);
        }
    }

    private GeneratedContent build(Repository repo, Type type, String text) {
        return GeneratedContent.builder()
                .repository(repo)
                .contentType(type.name())
                .generatedText(text)
                .build();
    }

    private String truncate(String msg) {
        if (msg == null) return "Unknown error";
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
