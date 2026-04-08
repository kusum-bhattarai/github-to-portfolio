package com.portfolio.backend.service;

import com.portfolio.backend.entity.AnalysisJob;
import com.portfolio.backend.entity.JobStatus;
import com.portfolio.backend.repository.AnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dual-storage job state management.
 *
 * Hot path (polling): reads from Redis in O(1) without touching the DB.
 * Cold path (durability): writes to PostgreSQL on every transition.
 * Redis keys expire after 24h — the DB is the source of truth for history.
 *
 * Key pattern: portfolio:job:{jobId}:status → status string
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobStateService {

    private static final String KEY_PREFIX = "portfolio:job:";
    private static final String STATUS_SUFFIX = ":status";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final AnalysisJobRepository jobRepository;

    /**
     * Transition a job to a new status, updating both Redis and DB atomically.
     */
    @Transactional
    public AnalysisJob transition(UUID jobId, JobStatus newStatus) {
        return transition(jobId, newStatus, null);
    }

    @Transactional
    public AnalysisJob transition(UUID jobId, JobStatus newStatus, String errorMessage) {
        AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        log.debug("Job {} transitioning {} → {}", jobId, job.getStatus(), newStatus);

        job.setStatus(newStatus);

        switch (newStatus) {
            case PROCESSING -> job.setStartedAt(OffsetDateTime.now());
            case COMPLETED, FAILED -> job.setCompletedAt(OffsetDateTime.now());
            default -> { /* no timestamp change for QUEUED/RETRYING */ }
        }

        if (errorMessage != null) job.setErrorMessage(errorMessage);

        AnalysisJob saved = jobRepository.save(job);

        // Mirror to Redis for fast polling
        String key = redisKey(jobId);
        redis.opsForValue().set(key, newStatus.name(), TTL);

        return saved;
    }

    /**
     * Fast status read — Redis first, DB fallback.
     */
    public JobStatus getStatus(UUID jobId) {
        String cached = redis.opsForValue().get(redisKey(jobId));
        if (cached != null) {
            return JobStatus.valueOf(cached);
        }
        // Redis miss (evicted or first call) — go to DB
        return jobRepository.findById(jobId)
                .map(AnalysisJob::getStatus)
                .orElse(null);
    }

    /**
     * Seed Redis when a new job is created.
     */
    public void seed(UUID jobId, JobStatus status) {
        redis.opsForValue().set(redisKey(jobId), status.name(), TTL);
    }

    private String redisKey(UUID jobId) {
        return KEY_PREFIX + jobId + STATUS_SUFFIX;
    }
}
