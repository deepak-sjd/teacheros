package com.teacheros.modules.auth;

import com.teacheros.modules.auth.dto.RefreshTokenRequest;
import com.teacheros.modules.auth.dto.RequestOtpRequest;
import com.teacheros.modules.auth.dto.TokenResponse;
import com.teacheros.modules.auth.dto.VerifyOtpRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        authService.requestOtp(request.phoneNumber());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        TokenResponse tokens = authService.verifyOtp(
                request.phoneNumber(), request.code(), request.displayName(), request.tenantName());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(tokens);
    }
}
