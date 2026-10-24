package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Shared-store OTP limiter (selected with
 * app.security.rate-limit.provider=redis).
 * Always fails CLOSED: if Redis is down, OTP requests are refused (503) rather
 * than letting SMS-pumping through unlimited.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.provider", havingValue = "redis")
public class RedisOtpRateLimiter implements OtpRateLimiter {

    // KEYS: phone zset, ip zset. ARGV: windowMs, phoneLimit, ipLimit, member.
    // Returns {status, oldestMs, nowMs}; status 0 ok, 1 phone limited, 2 ip
    // limited.
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> REQUEST_SCRIPT = new DefaultRedisScript<>("""
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            local win = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - win)
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now - win)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then
              local o = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
              return {1, tonumber(o[2]), now}
            end
            if redis.call('ZCARD', KEYS[2]) >= tonumber(ARGV[3]) then
              local o = redis.call('ZRANGE', KEYS[2], 0, 0, 'WITHSCORES')
              return {2, tonumber(o[2]), now}
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('ZADD', KEYS[2], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], win)
            redis.call('PEXPIRE', KEYS[2], win)
            return {0, 0, now}
            """, List.class);

    // KEYS: failure counter, lock. ARGV: maxFailures, lockoutMs.
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            if c >= tonumber(ARGV[1]) then redis.call('SET', KEYS[2], '1', 'PX', ARGV[2]) end
            return {c}
            """, List.class);

    private final StringRedisTemplate redis;
    private final int phoneLimit;
    private final int ipLimit;
    private final long windowMs;
    private final int maxFailures;
    private final long lockoutMs;

    public RedisOtpRateLimiter(
            StringRedisTemplate redis,
            @Value("${app.security.otp.request-limit:5}") int phoneLimit,
            @Value("${app.security.otp.ip-request-limit:20}") int ipLimit,
            @Value("${app.security.otp.request-window-minutes:10}") long windowMinutes,
            @Value("${app.security.otp.verify-max-failures:5}") int maxFailures,
            @Value("${app.security.otp.lockout-minutes:15}") long lockoutMinutes) {
        this.redis = redis;
        this.phoneLimit = phoneLimit;
        this.ipLimit = ipLimit;
        this.windowMs = windowMinutes * 60_000;
        this.maxFailures = maxFailures;
        this.lockoutMs = lockoutMinutes * 60_000;
    }

    @Override
    public void checkAndRecordRequest(String phoneNumber, String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        List<?> r = guarded(() -> redis.execute(REQUEST_SCRIPT,
                List.of("otp:req:phone:" + phoneId(phoneNumber), "otp:req:ip:" + ip),
                String.valueOf(windowMs), String.valueOf(phoneLimit), String.valueOf(ipLimit),
                UUID.randomUUID().toString()));
        long status = ((Number) r.get(0)).longValue();
        if (status != 0) {
            long retryMs = ((Number) r.get(1)).longValue() + windowMs - ((Number) r.get(2)).longValue();
            long minutes = Math.max(1, (retryMs + 59_999) / 60_000);
            String who = status == 1 ? "for this number" : "from this network";
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                    "Too many code requests " + who + ". Try again in " + minutes + " minute(s).");
        }
    }

    @Override
    public void assertVerifyAllowed(String phoneNumber) {
        Long ttlMs = guarded(() -> redis.getExpire("otp:lock:" + phoneId(phoneNumber),
                java.util.concurrent.TimeUnit.MILLISECONDS));
        if (ttlMs != null && ttlMs > 0) {
            long minutes = Math.max(1, (ttlMs + 59_999) / 60_000);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED",
                    "Too many incorrect codes. Try again in " + minutes + " minute(s).");
        }
    }

    @Override
    public void recordVerifyFailure(String phoneNumber) {
        String id = phoneId(phoneNumber);
        guarded(() -> redis.execute(FAILURE_SCRIPT, List.of("otp:fail:" + id, "otp:lock:" + id),
                String.valueOf(maxFailures), String.valueOf(lockoutMs)));
    }

    @Override
    public void recordVerifySuccess(String phoneNumber) {
        String id = phoneId(phoneNumber);
        guarded(() -> redis.delete(List.of("otp:fail:" + id, "otp:lock:" + id)));
    }

    private <T> T guarded(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMITER_UNAVAILABLE",
                    "Please try again shortly.");
        }
    }

    // Same normalisation as the in-memory limiter, then hashed so no phone number
    // sits in Redis.
    private static String phoneId(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(digits.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
