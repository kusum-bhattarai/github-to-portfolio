package com.portfolio.backend.kafka.event;

import java.util.List;
import java.util.UUID;

/**
 * Published to {@code repo.generation.completed} after the LLM returns successfully.
 * Carries the full generated content so the persistence consumer can write to DB
 * without needing to call the LLM again.
 */
public record GenerationCompletedEvent(
        UUID jobId,
        UUID repositoryId,
        String portfolioSummary,
        List<String> resumeBullets,
        List<String> techStack,
        List<String> projectTags,
        String interviewStory,
        String oneLinePitch,
        List<String> talkingPoints
) {}
