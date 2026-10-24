package com.teacheros.modules.security.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.provider", havingValue = "memory", matchIfMissing = true)
public class InMemoryApiRateLimiter implements ApiRateLimiter {

    private static final class Window {
        final long start;
        final long lengthMs;
        int count;

        Window(long start, long lengthMs) {
            this.start = start;
            this.lengthMs = lengthMs;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Decision tryAcquire(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long lengthMs = windowSeconds * 1000L;
        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now - old.start >= old.lengthMs) {
                Window fresh = new Window(now, lengthMs);
                fresh.count = 1;
                return fresh;
            }
            old.count++;
            return old;
        });
        long retry = Math.max(1, (w.start + w.lengthMs - now + 999) / 1000);
        return new Decision(w.count <= limit, retry);
    }

    @Scheduled(fixedDelayString = "${app.security.otp.cleanup-ms:600000}")
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start >= e.getValue().lengthMs);
    }
}
