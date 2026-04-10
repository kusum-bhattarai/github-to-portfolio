package com.portfolio.backend.kafka.event;

import java.util.UUID;

/**
 * Published to {@code repo.analysis.requested} by the controller when a user
 * submits repos for analysis. Consumed by {@link com.portfolio.backend.kafka.consumer.ExtractionStageConsumer}.
 *
 * {@code attempt} starts at 1 and increments on each retry from the DLQ consumer.
 */
public record AnalysisRequestedEvent(
        UUID jobId,
        UUID repositoryId,
        UUID userId,
        int attempt
) {}
