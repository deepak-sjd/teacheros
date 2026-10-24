package com.teacheros.modules.security.ratelimit;

/**
 * Fixed-window counter used by RateLimitFilter. Implementations: in-memory,
 * Redis.
 */
public interface ApiRateLimiter {

    Decision tryAcquire(String key, int limit, int windowSeconds);

    record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
