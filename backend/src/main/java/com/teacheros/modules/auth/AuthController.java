package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.auth.dto.RefreshTokenRequest;
import com.teacheros.modules.auth.dto.RequestOtpRequest;
import com.teacheros.modules.auth.dto.TokenResponse;
import com.teacheros.modules.auth.dto.VerifyOtpRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpRateLimiter otpRateLimiter;

    public AuthController(AuthService authService, OtpRateLimiter otpRateLimiter) {
        this.authService = authService;
        this.otpRateLimiter = otpRateLimiter;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody RequestOtpRequest request,
            HttpServletRequest http) {
        // getRemoteAddr, not X-Forwarded-For (client-spoofable). Behind a proxy set
        // server.forward-headers-strategy=native so this is the real client IP.
        otpRateLimiter.checkAndRecordRequest(request.phoneNumber(), http.getRemoteAddr());
        authService.requestOtp(request.phoneNumber());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        otpRateLimiter.assertVerifyAllowed(request.phoneNumber());
        TokenResponse tokens;
        try {
            tokens = authService.verifyOtp(
                    request.phoneNumber(), request.code(), request.displayName(), request.tenantName());
        } catch (ApiException e) {
            // Only a wrong code counts toward lockout - an expired code or missing signup
            // fields isn't someone guessing.
            if ("OTP_INCORRECT".equals(e.getCode())) {
                otpRateLimiter.recordVerifyFailure(request.phoneNumber());
            }
            throw e;
        }
        otpRateLimiter.recordVerifySuccess(request.phoneNumber());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(tokens);
    }
}
