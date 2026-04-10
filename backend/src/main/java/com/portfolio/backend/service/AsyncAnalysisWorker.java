package com.portfolio.backend.service;

import com.portfolio.backend.entity.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Background worker for repository analysis.
 *
 * Runs on the "analysisExecutor" thread pool.
 *
 * Retry strategy: exponential backoff with up to MAX_ATTEMPTS total attempts.
 * Delays: 2s → 4s → 8s (doubles each retry, capped at MAX_BACKOFF_MS).
 * All state transitions are mirrored to both Redis (fast polling) and
 * PostgreSQL (durability).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalysisWorker {

    private static final int MAX_ATTEMPTS   = 3;
    private static final long BASE_DELAY_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final AnalysisService analysisService;
    private final JobStateService jobStateService;

    @Async("analysisExecutor")
    public CompletableFuture<Void> run(UUID jobId, UUID repositoryId, UUID userId) {
        log.info("Worker starting — jobId={} repoId={}", jobId, repositoryId);
        jobStateService.transition(jobId, JobStatus.PROCESSING);

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                analysisService.analyzeById(repositoryId, userId);
                jobStateService.transition(jobId, JobStatus.COMPLETED);
                log.info("Worker completed — jobId={} attempt={}", jobId, attempt);
                return CompletableFuture.completedFuture(null);

            } catch (Exception e) {
                lastException = e;
                log.warn("Worker attempt {}/{} failed — jobId={}: {}", attempt, MAX_ATTEMPTS, jobId, e.getMessage());

                if (attempt < MAX_ATTEMPTS) {
                    long delayMs = Math.min(BASE_DELAY_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
                    log.info("Worker backing off {}ms before retry — jobId={}", delayMs, jobId);
                    jobStateService.transition(jobId, JobStatus.RETRYING);

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        jobStateService.transition(jobId, JobStatus.FAILED, "Worker thread interrupted");
                        return CompletableFuture.completedFuture(null);
                    }

                    jobStateService.transition(jobId, JobStatus.PROCESSING);
                }
            }
        }

        String errorMsg = lastException != null && lastException.getMessage() != null
                ? lastException.getMessage().substring(0, Math.min(lastException.getMessage().length(), 500))
                : "Unknown error after " + MAX_ATTEMPTS + " attempts";
        log.error("Worker exhausted all {} attempts — jobId={}: {}", MAX_ATTEMPTS, jobId, errorMsg);
        jobStateService.transition(jobId, JobStatus.FAILED, errorMsg);

        return CompletableFuture.completedFuture(null);
    }
}
