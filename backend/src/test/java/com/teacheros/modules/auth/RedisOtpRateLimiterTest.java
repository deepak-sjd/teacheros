package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisOtpRateLimiterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    RedisOtpRateLimiter instanceA;
    RedisOtpRateLimiter instanceB;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        template.execute((RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        });
        // 5 per phone, 20 per IP, 10 min window, lock after 5 failures for 15 min
        instanceA = new RedisOtpRateLimiter(template, 5, 20, 10, 5, 15);
        instanceB = new RedisOtpRateLimiter(template, 5, 20, 10, 5, 15);
    }

    @Test
    void twoInstancesShareOneCounterSoTheSixthRequestIs429() {
        for (int i = 0; i < 5; i++) {
            (i % 2 == 0 ? instanceA : instanceB).checkAndRecordRequest("+919876543210", "1.1.1." + i);
        }
        assertThatThrownBy(() -> instanceB.checkAndRecordRequest("919876543210", "1.1.1.9"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("OTP_RATE_LIMITED");
                });
    }

    @Test
    void lockoutOnOneInstanceBlocksTheOther() {
        for (int i = 0; i < 5; i++) {
            instanceA.recordVerifyFailure("+919876543210");
        }
        assertThatThrownBy(() -> instanceB.assertVerifyAllowed("+919876543210"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("OTP_LOCKED"));
    }

    @Test
    void successClearsFailures() {
        for (int i = 0; i < 4; i++) {
            instanceA.recordVerifyFailure("+919876543210");
        }
        instanceB.recordVerifySuccess("+919876543210");
        instanceA.recordVerifyFailure("+919876543210");
        instanceA.assertVerifyAllowed("+919876543210"); // would be locked if the count had survived
    }
}
