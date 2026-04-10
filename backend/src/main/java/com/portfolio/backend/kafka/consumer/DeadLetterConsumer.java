package com.portfolio.backend.kafka.consumer;

import com.portfolio.backend.entity.JobStatus;
import com.portfolio.backend.kafka.KafkaTopics;
import com.portfolio.backend.kafka.event.AnalysisRequestedEvent;
import com.portfolio.backend.kafka.event.GenerationFailedEvent;
import com.portfolio.backend.service.JobStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Dead Letter Consumer — final arbiter of pipeline failures.
 *
 * Retry policy:
 *   - attempt < MAX_ATTEMPTS → transition RETRYING, re-publish to repo.analysis.requested
 *   - attempt >= MAX_ATTEMPTS → transition FAILED, log for alerting
 *
 * Re-publishing to the head of the pipeline (repo.analysis.requested) means the full
 * extraction + generation + persistence chain is retried. This is intentional: transient
 * GitHub API or LLM failures at any stage are recoverable by restarting from the top.
 *
 * Tradeoff: re-extracting evidence on a generation failure is wasteful, but it ensures
 * correctness if the snapshot write also failed. For higher-volume systems, per-stage
 * retry topics would be more efficient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private static final int MAX_ATTEMPTS = 3;
    private static final String GROUP = "portfolio-dlq";

    private final JobStateService jobStateService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.GENERATION_FAILED, groupId = GROUP,
                   containerFactory = "kafkaListenerContainerFactory")
    public void onGenerationFailed(GenerationFailedEvent event) {
        log.warn("DLQ received — jobId={} stage={} attempt={}/{} error={}",
                event.jobId(), event.stage(), event.attempt(), MAX_ATTEMPTS, event.errorMessage());

        if (event.attempt() < MAX_ATTEMPTS && event.userId() != null) {
            int nextAttempt = event.attempt() + 1;
            log.info("Retrying — jobId={} nextAttempt={}", event.jobId(), nextAttempt);
            jobStateService.transition(event.jobId(), JobStatus.RETRYING);

            AnalysisRequestedEvent retry = new AnalysisRequestedEvent(
                    event.jobId(), event.repositoryId(), event.userId(), nextAttempt);
            kafkaTemplate.send(KafkaTopics.ANALYSIS_REQUESTED, event.repositoryId().toString(), retry);
        } else {
            log.error("Job permanently failed — jobId={} stage={} after {} attempts: {}",
                    event.jobId(), event.stage(), event.attempt(), event.errorMessage());
            if (event.jobId() != null) {
                jobStateService.transition(event.jobId(), JobStatus.FAILED, event.errorMessage());
            }
        }
    }
}
