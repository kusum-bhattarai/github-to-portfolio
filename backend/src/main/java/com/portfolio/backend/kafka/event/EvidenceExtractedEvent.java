package com.portfolio.backend.kafka.event;

import java.util.UUID;

/**
 * Published to {@code repo.evidence.extracted} after the extraction stage completes.
 * Carries the full evidence payload inline so the generation consumer is stateless.
 */
public record EvidenceExtractedEvent(
        UUID jobId,
        UUID repositoryId,
        UUID userId,
        int attempt,
        EvidencePayload evidence
) {}
