package com.portfolio.backend.repository;

import com.portfolio.backend.entity.AnalysisJob;
import com.portfolio.backend.entity.JobStatus;
import com.portfolio.backend.entity.Repository;
import com.portfolio.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    @Query("SELECT j FROM AnalysisJob j JOIN FETCH j.repository WHERE j.id = :id AND j.user = :user")
    Optional<AnalysisJob> findByIdAndUser(@Param("id") UUID id, @Param("user") User user);

    @Query("SELECT j FROM AnalysisJob j JOIN FETCH j.repository WHERE j.user = :user ORDER BY j.createdAt DESC")
    List<AnalysisJob> findByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT j FROM AnalysisJob j JOIN FETCH j.repository WHERE j.user = :user ORDER BY j.createdAt DESC")
    Page<AnalysisJob> findByUser(@Param("user") User user, Pageable pageable);

    /** Idempotency check — find an active (non-terminal) job for this repo. */
    @Query("SELECT j FROM AnalysisJob j JOIN FETCH j.repository WHERE j.repository = :repo AND j.status NOT IN :terminalStatuses ORDER BY j.createdAt DESC")
    Optional<AnalysisJob> findActiveJobForRepo(
            @Param("repo") Repository repo,
            @Param("terminalStatuses") List<JobStatus> terminalStatuses);
}
