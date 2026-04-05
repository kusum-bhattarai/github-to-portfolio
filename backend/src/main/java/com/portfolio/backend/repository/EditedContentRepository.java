package com.portfolio.backend.repository;

import com.portfolio.backend.entity.EditedContent;
import com.portfolio.backend.entity.GeneratedContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EditedContentRepository extends JpaRepository<EditedContent, UUID> {
    Optional<EditedContent> findByGeneratedContent(GeneratedContent generatedContent);
    List<EditedContent> findByGeneratedContentRepositoryId(UUID repositoryId);
}
