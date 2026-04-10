package com.portfolio.backend.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all pipeline topics so they are created automatically on startup
 * when auto.create.topics.enable is true (local/dev) or via AdminClient (prod).
 *
 * Partition counts:
 *   - analysis/evidence/completed: 3 partitions for parallel consumer throughput
 *   - failed (DLQ): 1 partition — ordering matters for DLQ inspection/alerting
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic analysisRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.ANALYSIS_REQUESTED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic evidenceExtractedTopic() {
        return TopicBuilder.name(KafkaTopics.EVIDENCE_EXTRACTED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic generationCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.GENERATION_COMPLETED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic generationFailedTopic() {
        return TopicBuilder.name(KafkaTopics.GENERATION_FAILED)
                .partitions(1).replicas(1).build();
    }
}
