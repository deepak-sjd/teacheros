package com.teacheros.modules.lead;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicEnquiryRateLimiterTest {

    @Test
    void blocksAfterLimit_perKey_andRecoversAfterWindow() {
        AtomicLong now = new AtomicLong(1_000);
        PublicEnquiryRateLimiter limiter = new PublicEnquiryRateLimiter(2, Duration.ofMinutes(10), now::get);

        assertTrue(limiter.tryAcquire("ip:1.1.1.1"));
        assertTrue(limiter.tryAcquire("ip:1.1.1.1"));
        assertFalse(limiter.tryAcquire("ip:1.1.1.1"));
        assertTrue(limiter.tryAcquire("ip:2.2.2.2")); // other callers unaffected

        now.addAndGet(Duration.ofMinutes(11).toMillis());
        assertTrue(limiter.tryAcquire("ip:1.1.1.1"));
    }
}
