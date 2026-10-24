package com.teacheros.modules.compliance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "guardian_phone", nullable = false)
    private String guardianPhone;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "text_version", nullable = false)
    private String textVersion;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.grantedAt == null) {
            this.grantedAt = this.createdAt;
        }
    }

    public ConsentRecord(UUID tenantId, UUID studentId, String guardianPhone, String purpose, String textVersion) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.guardianPhone = guardianPhone;
        this.purpose = purpose;
        this.textVersion = textVersion;
    }
}
