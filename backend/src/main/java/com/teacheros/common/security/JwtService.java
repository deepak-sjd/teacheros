package com.teacheros.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies short-lived JWT access tokens.
 *
 * Refresh tokens are intentionally NOT JWTs - they are opaque random values
 * stored (hashed) in the refresh_tokens table so they can actually be
 * revoked. A JWT refresh token can't be revoked without a blocklist, which
 * defeats the point of using a JWT in the first place. See RefreshTokenService.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters. Set JWT_SECRET in the environment " +
                    "- never rely on the checked-in dev default outside local development.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public String issueAccessToken(UUID userId, UUID tenantId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenantId", tenantId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the authenticated principal if the token is valid and unexpired,
     * or empty if it's missing, malformed, expired, or tampered with.
     * Callers must treat "empty" as "unauthenticated," never throw raw
     * parsing exceptions up to the client.
     */
    public Optional<AuthenticatedPrincipal> parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
            String role = claims.get("role", String.class);

            return Optional.of(new AuthenticatedPrincipal(userId, tenantId, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
