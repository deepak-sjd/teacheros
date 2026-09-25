package com.teacheros.modules.attendance;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Signs and verifies the short-lived token embedded in a "scan to mark
 * present" QR code. Deliberately its own signer (not JwtService's access
 * tokens) - different lifetime (minutes, not the login session), different
 * claim shape, and a compromised QR token should never be usable as a login
 * token or vice versa. Reuses app.jwt.secret as the key material purely for
 * convenience (one secret to manage), not because the token formats are
 * related.
 */
@Service
public class AttendanceQrTokenService {

    private static final String TENANT_CLAIM = "tenantId";
    private static final String BATCH_CLAIM = "batchId";
    private static final String DATE_CLAIM = "sessionDate";

    private final SecretKey signingKey;
    private final Duration ttl;

    public AttendanceQrTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.attendance.qr-ttl-minutes:10}") long ttlMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public record QrPayload(UUID tenantId, UUID batchId, LocalDate sessionDate) {
    }

    public String issue(UUID tenantId, UUID batchId, LocalDate sessionDate) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim(TENANT_CLAIM, tenantId.toString())
                .claim(BATCH_CLAIM, batchId.toString())
                .claim(DATE_CLAIM, sessionDate.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public Instant expiryFor(Instant issuedAt) {
        return issuedAt.plus(ttl);
    }

    /** Empty if the token is missing, malformed, expired, or tampered with. */
    public Optional<QrPayload> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new QrPayload(
                    UUID.fromString(claims.get(TENANT_CLAIM, String.class)),
                    UUID.fromString(claims.get(BATCH_CLAIM, String.class)),
                    LocalDate.parse(claims.get(DATE_CLAIM, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
