package com.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "repo_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepoSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(name = "readme_content", columnDefinition = "TEXT")
    private String readmeContent;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Array(length = 100)
    @Column(name = "detected_stack")
    private List<String> detectedStack;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_signals", columnDefinition = "jsonb")
    private Map<String, Object> extractedSignals;

    @Column(name = "project_type")
    private String projectType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_dependencies", columnDefinition = "jsonb")
    private Map<String, Object> parsedDependencies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quantitative_metrics", columnDefinition = "jsonb")
    private Map<String, Object> quantitativeMetrics;

    @Column(name = "analyzed_at", nullable = false)
    private OffsetDateTime analyzedAt;

    @PrePersist
    @PreUpdate
    void prePersist() {
        analyzedAt = OffsetDateTime.now();
    }
}
