package com.portfolio.backend.repository;

import com.portfolio.backend.entity.GitHubConnection;
import com.portfolio.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GitHubConnectionRepository extends JpaRepository<GitHubConnection, UUID> {
    Optional<GitHubConnection> findByUser(User user);
}
