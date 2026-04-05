package com.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "edited_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_content_id", nullable = false)
    private GeneratedContent generatedContent;

    @Column(name = "edited_text", nullable = false, columnDefinition = "TEXT")
    private String editedText;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
