package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryOtpRateLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private MutableClock clock;
    private InMemoryOtpRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        // 5 per phone, 20 per IP, 10 min window, lock after 5 failures for 15 min
        limiter = new InMemoryOtpRateLimiter(5, 20, Duration.ofMinutes(10), 5, Duration.ofMinutes(15), clock);
    }

    @Test
    void sixthRequestForSamePhoneReturns429() {
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordRequest("+919876543210", "1.1.1." + i);
        }
        assertThatThrownBy(() -> limiter.checkAndRecordRequest("+919876543210", "1.1.1.9"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("OTP_RATE_LIMITED");
                });
    }

    @Test
    void phoneFormatVariantsShareOneBudget() {
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordRequest(i % 2 == 0 ? "+919876543210" : "919876543210", "1.1.1." + i);
        }
        assertThatThrownBy(() -> limiter.checkAndRecordRequest("+91 98765 43210", "2.2.2.2"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void budgetResetsAfterTheWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordRequest("+919876543210", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(11));
        assertThatCode(() -> limiter.checkAndRecordRequest("+919876543210", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void perIpLimitBlocksManyPhonesFromOneAddress() {
        for (int i = 0; i < 20; i++) {
            limiter.checkAndRecordRequest("+91900000" + String.format("%04d", i), "9.9.9.9");
        }
        assertThatThrownBy(() -> limiter.checkAndRecordRequest("+919111111111", "9.9.9.9"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void blockedRequestDoesNotConsumeIpBudget() {
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordRequest("+919876543210", "7.7.7.7");
        }
        for (int i = 0; i < 10; i++) {
            try {
                limiter.checkAndRecordRequest("+919876543210", "7.7.7.7");
            } catch (ApiException ignored) {
            }
        }
        // IP used 5 of 20; a different phone from the same IP must still be fine.
        assertThatCode(() -> limiter.checkAndRecordRequest("+919222222222", "7.7.7.7")).doesNotThrowAnyException();
    }

    @Test
    void fiveWrongCodesLockVerifyThenLockExpires() {
        for (int i = 0; i < 5; i++) {
            limiter.assertVerifyAllowed("+919876543210");
            limiter.recordVerifyFailure("+919876543210");
        }
        assertThatThrownBy(() -> limiter.assertVerifyAllowed("+919876543210"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("OTP_LOCKED");
                });

        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> limiter.assertVerifyAllowed("+919876543210")).doesNotThrowAnyException();
    }

    @Test
    void successfulVerifyClearsTheFailureCount() {
        for (int i = 0; i < 4; i++) {
            limiter.recordVerifyFailure("+919876543210");
        }
        limiter.recordVerifySuccess("+919876543210");
        limiter.recordVerifyFailure("+919876543210"); // would be the 5th if not cleared
        assertThatCode(() -> limiter.assertVerifyAllowed("+919876543210")).doesNotThrowAnyException();
    }
}
