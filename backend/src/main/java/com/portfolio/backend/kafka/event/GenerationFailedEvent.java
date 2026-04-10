package com.portfolio.backend.kafka.event;

import java.util.UUID;

/**
 * Published to {@code repo.generation.failed} (the DLQ topic) when any pipeline stage
 * encounters an unrecoverable error.
 *
 * {@code stage} identifies which consumer produced the failure:
 *   "extraction" | "generation" | "persistence"
 *
 * {@code attempt} is forwarded so the DLQ consumer can decide whether to retry
 * or mark the job as permanently failed.
 */
public record GenerationFailedEvent(
        UUID jobId,
        UUID repositoryId,
        UUID userId,
        String stage,
        String errorMessage,
        int attempt
) {}
