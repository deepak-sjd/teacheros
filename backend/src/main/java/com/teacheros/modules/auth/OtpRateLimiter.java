package com.teacheros.modules.auth;

/**
 * Throttles OTP abuse. The in-memory implementation is the default; a
 * Redis-backed one only needs to implement this interface and be selected
 * with app.security.rate-limiter=redis. All "blocked" outcomes throw
 * ApiException(429), which GlobalExceptionHandler turns into the standard
 * ErrorResponse.
 */
public interface OtpRateLimiter {

    /**
     * Checks both the per-phone and per-IP budgets, then counts this request
     * against them.
     */
    void checkAndRecordRequest(String phoneNumber, String clientIp);

    /** Throws if this phone is currently locked out from verifying. */
    void assertVerifyAllowed(String phoneNumber);

    void recordVerifyFailure(String phoneNumber);

    void recordVerifySuccess(String phoneNumber);
}
