package com.portfolio.backend.kafka.event;

import com.portfolio.backend.service.EvidenceExtractor;

import java.util.List;
import java.util.Map;

/**
 * JSON-serializable mirror of {@link EvidenceExtractor.ExtractionResult} for Kafka transport.
 *
 * Carrying evidence inline in the event avoids an extra DB round-trip in the generation stage
 * and keeps each consumer stateless with respect to inter-stage data.
 */
public record EvidencePayload(
        String readmeContent,
        List<String> detectedStack,
        Map<String, Object> signals,
        String projectType,
        Map<String, Object> parsedDependencies,
        Map<String, Object> quantitativeMetrics,
        Map<String, Object> commitSignals
) {
    public static EvidencePayload from(EvidenceExtractor.ExtractionResult r) {
        return new EvidencePayload(
                r.readmeContent(),
                r.detectedStack(),
                r.signals(),
                r.projectType(),
                r.parsedDependencies(),
                r.quantitativeMetrics(),
                r.commitSignals()
        );
    }

    public EvidenceExtractor.ExtractionResult toExtractionResult() {
        return new EvidenceExtractor.ExtractionResult(
                readmeContent, detectedStack, signals,
                projectType, parsedDependencies, quantitativeMetrics, commitSignals
        );
    }
}
