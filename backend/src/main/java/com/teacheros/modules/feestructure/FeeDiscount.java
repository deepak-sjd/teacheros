package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_discounts")
@Getter
@Setter
@NoArgsConstructor
public class FeeDiscount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // Null = applies across every head.
    @Column(name = "fee_head_id")
    private UUID feeHeadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private AmountType discountType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private String reason;

    @Column(name = "approved_by_user_id", nullable = false)
    private UUID approvedByUserId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public FeeDiscount(UUID tenantId, UUID studentId, UUID feeHeadId, AmountType discountType, BigDecimal value,
            String reason, UUID approvedByUserId) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.feeHeadId = feeHeadId;
        this.discountType = discountType;
        this.value = value;
        this.reason = reason;
        this.approvedByUserId = approvedByUserId;
    }
}
