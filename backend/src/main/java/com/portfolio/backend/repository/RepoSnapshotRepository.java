package com.portfolio.backend.repository;

import com.portfolio.backend.entity.RepoSnapshot;
import com.portfolio.backend.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepoSnapshotRepository extends JpaRepository<RepoSnapshot, UUID> {
    Optional<RepoSnapshot> findByRepository(Repository repository);
}
