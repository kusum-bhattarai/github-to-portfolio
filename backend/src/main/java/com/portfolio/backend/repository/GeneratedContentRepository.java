package com.portfolio.backend.repository;

import com.portfolio.backend.entity.GeneratedContent;
import com.portfolio.backend.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GeneratedContentRepository extends JpaRepository<GeneratedContent, UUID> {
    List<GeneratedContent> findByRepositoryOrderByCreatedAtDesc(Repository repository);
    void deleteByRepository(Repository repository);
}
