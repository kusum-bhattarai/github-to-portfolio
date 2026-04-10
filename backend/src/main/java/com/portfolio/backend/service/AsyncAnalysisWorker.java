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
 * Runs on the "analysisExecutor" thread pool. On transient failure the job
 * transitions to RETRYING, waits 5 s, then attempts once more before marking
 * it FAILED. All state transitions are mirrored to both Redis (fast polling)
 * and PostgreSQL (durability).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalysisWorker {

    private static final int RETRY_DELAY_MS = 5_000;

    private final AnalysisService analysisService;
    private final JobStateService jobStateService;

    @Async("analysisExecutor")
    public CompletableFuture<Void> run(UUID jobId, UUID repositoryId, UUID userId) {
        log.info("Worker starting — jobId={} repoId={}", jobId, repositoryId);
        jobStateService.transition(jobId, JobStatus.PROCESSING);

        try {
            analysisService.analyzeById(repositoryId, userId);
            jobStateService.transition(jobId, JobStatus.COMPLETED);
            log.info("Worker completed — jobId={}", jobId);

        } catch (Exception firstException) {
            log.warn("Worker first attempt failed — jobId={}: {}", jobId, firstException.getMessage());
            jobStateService.transition(jobId, JobStatus.RETRYING);

            try {
                Thread.sleep(RETRY_DELAY_MS);
                log.info("Worker retrying — jobId={}", jobId);
                jobStateService.transition(jobId, JobStatus.PROCESSING);

                analysisService.analyzeById(repositoryId, userId);
                jobStateService.transition(jobId, JobStatus.COMPLETED);
                log.info("Worker completed on retry — jobId={}", jobId);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                jobStateService.transition(jobId, JobStatus.FAILED, "Worker thread interrupted");

            } catch (Exception retryException) {
                log.error("Worker retry failed — jobId={}: {}", jobId, retryException.getMessage());
                String errorMsg = retryException.getMessage() != null
                        ? retryException.getMessage().substring(0, Math.min(retryException.getMessage().length(), 500))
                        : "Unknown error";
                jobStateService.transition(jobId, JobStatus.FAILED, errorMsg);
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}
