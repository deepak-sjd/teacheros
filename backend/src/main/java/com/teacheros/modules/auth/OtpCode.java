package com.teacheros.modules.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_codes")
@Getter
@Setter
@NoArgsConstructor
public class OtpCode {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    // Never store the raw OTP - only a hash, same discipline as a password.
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public OtpCode(String phoneNumber, String codeHash, Instant expiresAt) {
        this.phoneNumber = phoneNumber;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable() {
        return consumedAt == null && expiresAt.isAfter(Instant.now()) && attemptCount < 5;
    }
}
