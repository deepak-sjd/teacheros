package com.teacheros.modules.security.ratelimit;

import com.teacheros.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.provider", havingValue = "redis")
public class RedisApiRateLimiter implements ApiRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisApiRateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            if c == 1 or redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return {c, redis.call('TTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties props;

    public RedisApiRateLimiter(StringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public Decision tryAcquire(String key, int limit, int windowSeconds) {
        try {
            List<?> r = redis.execute(SCRIPT, List.of("rl:" + key), String.valueOf(windowSeconds));
            long count = ((Number) r.get(0)).longValue();
            long ttl = ((Number) r.get(1)).longValue();
            return new Decision(count <= limit, Math.max(1, ttl));
        } catch (RuntimeException e) {
            if (props.isFailOpen()) {
                log.error("Redis rate limiter unavailable, allowing request: {}", e.getMessage());
                return new Decision(true, 0);
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMITER_UNAVAILABLE",
                    "Please try again shortly.");
        }
    }
}
