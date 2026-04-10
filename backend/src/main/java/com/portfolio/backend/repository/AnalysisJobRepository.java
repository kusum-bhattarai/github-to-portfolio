package com.portfolio.backend.repository;

import com.portfolio.backend.entity.AnalysisJob;
import com.portfolio.backend.entity.User;
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
}
