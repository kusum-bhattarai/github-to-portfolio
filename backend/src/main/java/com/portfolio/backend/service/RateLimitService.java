package com.portfolio.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed rate limiter for analysis job submissions.
 *
 * Strategy: sliding window counter per user per hour.
 * Key: portfolio:ratelimit:{userId}:analysis  →  count (TTL 1h)
 *
 * Limit: MAX_ANALYSES_PER_HOUR analyses per user per rolling hour.
 * This prevents a user from hammering the GitHub API and LLM by submitting
 * hundreds of repos in quick succession.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final int MAX_ANALYSES_PER_HOUR = 20;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final String KEY_PREFIX = "portfolio:ratelimit:";

    private final StringRedisTemplate redis;

    /**
     * Returns true if the user is allowed to submit more analyses, false if rate-limited.
     * Increments the counter atomically on each allowed call.
     */
    public boolean allowAnalysis(UUID userId) {
        String key = KEY_PREFIX + userId + ":analysis";

        Long count = redis.opsForValue().increment(key);
        if (count == null) return true; // Redis unavailable — fail open

        if (count == 1) {
            // First call in this window — set the expiry
            redis.expire(key, WINDOW);
        }

        if (count > MAX_ANALYSES_PER_HOUR) {
            log.warn("Rate limit hit for user {} — {} analyses in current window", userId, count);
            return false;
        }

        return true;
    }

    /** Returns remaining allowed analyses in the current window (for headers/debugging). */
    public int remaining(UUID userId) {
        String key = KEY_PREFIX + userId + ":analysis";
        String val = redis.opsForValue().get(key);
        if (val == null) return MAX_ANALYSES_PER_HOUR;
        int used = Integer.parseInt(val);
        return Math.max(0, MAX_ANALYSES_PER_HOUR - used);
    }
}
