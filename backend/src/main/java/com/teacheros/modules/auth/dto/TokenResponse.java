package com.teacheros.modules.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
