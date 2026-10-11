package com.teacheros.modules.lead;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory sliding-window limiter for the unauthenticated enquiry endpoint.
 * Single-instance only: if the backend is ever scaled to several instances,
 * swap this for a shared store (Redis / bucket4j) behind the same method.
 */
@Component
public class PublicEnquiryRateLimiter {

    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxRequests;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Autowired
    public PublicEnquiryRateLimiter(
            @Value("${app.public.enquiry.max-per-window:5}") int maxRequests,
            @Value("${app.public.enquiry.window-minutes:10}") long windowMinutes) {
        this(maxRequests, Duration.ofMinutes(windowMinutes), System::currentTimeMillis);
    }

    PublicEnquiryRateLimiter(int maxRequests, Duration window, LongSupplier clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /**
     * @return true if this call is allowed (and counted), false if the key is over
     *         its limit.
     */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        if (hits.size() > PURGE_THRESHOLD) {
            purge(now);
        }
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                queue.pollFirst();
            }
            if (queue.size() >= maxRequests) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    private void purge(long now) {
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last > windowMillis;
            }
        });
    }
}
