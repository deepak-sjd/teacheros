package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding-window limiter held in this JVM's memory. Limits are per
 * instance (state is lost on restart and not shared across instances) -
 * fine for a single-node pilot; swap in a shared-store implementation
 * before running several backend instances.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limiter", havingValue = "memory", matchIfMissing = true)
public class InMemoryOtpRateLimiter implements OtpRateLimiter {

    private final int phoneLimit;
    private final int ipLimit;
    private final Duration requestWindow;
    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;

    private final Map<String, Deque<Instant>> phoneRequests = new HashMap<>();
    private final Map<String, Deque<Instant>> ipRequests = new HashMap<>();
    private final Map<String, Failures> failures = new HashMap<>();

    private static final class Failures {
        int count;
        Instant lastFailure;
        Instant lockedUntil;
    }

    @Autowired
    public InMemoryOtpRateLimiter(
            @Value("${app.security.otp.request-limit:5}") int phoneLimit,
            @Value("${app.security.otp.ip-request-limit:20}") int ipLimit,
            @Value("${app.security.otp.request-window-minutes:10}") long windowMinutes,
            @Value("${app.security.otp.verify-max-failures:5}") int maxFailures,
            @Value("${app.security.otp.lockout-minutes:15}") long lockoutMinutes) {
        this(phoneLimit, ipLimit, Duration.ofMinutes(windowMinutes), maxFailures, Duration.ofMinutes(lockoutMinutes),
                Clock.systemUTC());
    }

    // Visible for tests (injectable clock).
    InMemoryOtpRateLimiter(int phoneLimit, int ipLimit, Duration requestWindow, int maxFailures, Duration lockout,
            Clock clock) {
        this.phoneLimit = phoneLimit;
        this.ipLimit = ipLimit;
        this.requestWindow = requestWindow;
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.clock = clock;
    }

    @Override
    public synchronized void checkAndRecordRequest(String phoneNumber, String clientIp) {
        Instant now = clock.instant();
        String phoneKey = normalize(phoneNumber);
        String ipKey = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;

        Deque<Instant> phoneWindow = window(phoneRequests, phoneKey, now);
        Deque<Instant> ipWindow = window(ipRequests, ipKey, now);

        // Check both before recording either, so a blocked request doesn't
        // eat into the other budget.
        if (phoneWindow.size() >= phoneLimit) {
            throw tooMany("OTP_RATE_LIMITED", "Too many code requests for this number.", phoneWindow.peekFirst(), now);
        }
        if (ipWindow.size() >= ipLimit) {
            throw tooMany("OTP_RATE_LIMITED", "Too many code requests from this network.", ipWindow.peekFirst(), now);
        }
        phoneWindow.addLast(now);
        ipWindow.addLast(now);
    }

    @Override
    public synchronized void assertVerifyAllowed(String phoneNumber) {
        Instant now = clock.instant();
        String key = normalize(phoneNumber);
        Failures f = failures.get(key);
        if (f == null) {
            return;
        }
        if (f.lockedUntil != null) {
            if (now.isBefore(f.lockedUntil)) {
                long minutes = Math.max(1, Duration.between(now, f.lockedUntil).toMinutes() + 1);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED",
                        "Too many incorrect codes. Try again in " + minutes + " minute(s).");
            }
            failures.remove(key); // lock expired
        }
    }

    @Override
    public synchronized void recordVerifyFailure(String phoneNumber) {
        Instant now = clock.instant();
        String key = normalize(phoneNumber);
        Failures f = failures.computeIfAbsent(key, k -> new Failures());
        // Old failures decay: a stale count from long ago shouldn't count toward a
        // lockout today.
        if (f.lastFailure != null && Duration.between(f.lastFailure, now).compareTo(lockout) > 0) {
            f.count = 0;
        }
        f.count++;
        f.lastFailure = now;
        if (f.count >= maxFailures) {
            f.lockedUntil = now.plus(lockout);
        }
    }

    @Override
    public synchronized void recordVerifySuccess(String phoneNumber) {
        failures.remove(normalize(phoneNumber));
    }

    /** Drops expired entries so the maps can't grow without bound. */
    @Scheduled(fixedDelayString = "${app.security.otp.cleanup-ms:600000}")
    public synchronized void purgeExpired() {
        Instant now = clock.instant();
        phoneRequests.values().removeIf(d -> prune(d, now).isEmpty());
        ipRequests.values().removeIf(d -> prune(d, now).isEmpty());
        failures.values().removeIf(f -> (f.lockedUntil != null && now.isAfter(f.lockedUntil))
                || (f.lockedUntil == null && f.lastFailure != null
                        && Duration.between(f.lastFailure, now).compareTo(lockout) > 0));
    }

    private Deque<Instant> window(Map<String, Deque<Instant>> map, String key, Instant now) {
        return prune(map.computeIfAbsent(key, k -> new ArrayDeque<>()), now);
    }

    private Deque<Instant> prune(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(requestWindow);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
            deque.removeFirst();
        }
        return deque;
    }

    private ApiException tooMany(String code, String prefix, Instant oldest, Instant now) {
        long minutes = Math.max(1, Duration.between(now, oldest.plus(requestWindow)).toMinutes() + 1);
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code,
                prefix + " Try again in " + minutes + " minute(s).");
    }

    // "+91 98765 43210" and "919876543210" are the same phone for limiting
    // purposes.
    private String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }
}
