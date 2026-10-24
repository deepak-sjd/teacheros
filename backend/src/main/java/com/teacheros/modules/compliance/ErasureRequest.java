package com.teacheros.modules.compliance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "erasure_requests")
@Getter
@Setter
@NoArgsConstructor
public class ErasureRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "requested_by_role", nullable = false)
    private String requestedByRole;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ErasureStatus status = ErasureStatus.PENDING;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public ErasureRequest(UUID tenantId, UUID studentId, UUID requestedByUserId, String requestedByRole,
            String reason) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.requestedByUserId = requestedByUserId;
        this.requestedByRole = requestedByRole;
        this.reason = reason;
    }
}
