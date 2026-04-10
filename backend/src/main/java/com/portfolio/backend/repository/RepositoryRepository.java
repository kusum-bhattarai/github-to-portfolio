package com.portfolio.backend.repository;

import com.portfolio.backend.entity.Repository;
import com.portfolio.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepository extends JpaRepository<Repository, UUID> {
    List<Repository> findByUserOrderByStarsDesc(User user);
    Optional<Repository> findByUserAndGithubRepoId(User user, Long githubRepoId);
}
