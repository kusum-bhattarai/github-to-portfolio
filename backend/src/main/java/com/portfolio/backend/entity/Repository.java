package com.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String visibility;

    @Column(name = "primary_language")
    private String primaryLanguage;

    @Column(nullable = false)
    private Integer stars;

    @Column(nullable = false)
    private Integer forks;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Array(length = 50)
    private List<String> topics;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name = "synced_at", nullable = false)
    private OffsetDateTime syncedAt;

    @PrePersist
    @PreUpdate
    void prePersist() {
        syncedAt = OffsetDateTime.now();
    }
}
