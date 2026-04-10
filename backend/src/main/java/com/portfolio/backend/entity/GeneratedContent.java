package com.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "generated_text", nullable = false, columnDefinition = "TEXT")
    private String generatedText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public enum Type {
        PORTFOLIO_SUMMARY,
        RESUME_BULLETS,
        TECH_STACK,
        PROJECT_TAGS,
        INTERVIEW_STORY,
        ONE_SENTENCE_PITCH,
        TALKING_POINTS
    }
}
