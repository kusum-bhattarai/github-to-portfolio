package com.portfolio.backend.kafka;

/**
 * Canonical Kafka topic names for the analysis pipeline.
 *
 * Pipeline flow:
 *   repo.analysis.requested
 *     → ExtractionStageConsumer
 *     → repo.evidence.extracted
 *     → GenerationStageConsumer
 *     → repo.generation.completed  (success)
 *     → repo.generation.failed     (any stage failure)
 *
 * repo.generation.completed → PersistenceStageConsumer (saves to DB, marks COMPLETED)
 * repo.generation.failed    → DeadLetterConsumer (retry or mark FAILED)
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ANALYSIS_REQUESTED   = "repo.analysis.requested";
    public static final String EVIDENCE_EXTRACTED   = "repo.evidence.extracted";
    public static final String GENERATION_COMPLETED = "repo.generation.completed";
    public static final String GENERATION_FAILED    = "repo.generation.failed";
}
