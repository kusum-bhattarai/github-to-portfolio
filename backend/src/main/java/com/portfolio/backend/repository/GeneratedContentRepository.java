package com.portfolio.backend.repository;

import com.portfolio.backend.entity.GeneratedContent;
import com.portfolio.backend.entity.Repository;
import com.portfolio.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GeneratedContentRepository extends JpaRepository<GeneratedContent, UUID> {
    List<GeneratedContent> findByRepositoryOrderByCreatedAtDesc(Repository repository);
    void deleteByRepository(Repository repository);

    @Query("SELECT DISTINCT gc.repository FROM GeneratedContent gc WHERE gc.repository.user = :user ORDER BY gc.repository.stars DESC")
    List<Repository> findAnalyzedRepositoriesByUser(@Param("user") User user);
}
