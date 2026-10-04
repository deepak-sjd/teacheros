package com.teacheros.modules.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    // Contact info only - login stays phone+OTP exclusively, this is never
    // used to authenticate. Nullable since nothing in the signup/OTP flow
    // collects it today; a user fills it in later via Settings if they want.
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public User(UUID tenantId, String phoneNumber, String displayName, Role role) {
        this.tenantId = tenantId;
        this.phoneNumber = phoneNumber;
        this.displayName = displayName;
        this.role = role;
    }
}
