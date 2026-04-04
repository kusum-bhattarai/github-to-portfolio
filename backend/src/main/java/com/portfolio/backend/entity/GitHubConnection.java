package com.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "encrypted_access_token", nullable = false)
    private String encryptedAccessToken;

    private String scopes;

    @Column(name = "connected_at", nullable = false)
    private OffsetDateTime connectedAt;

    @PrePersist
    void prePersist() {
        connectedAt = OffsetDateTime.now();
    }
}
