package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row per tenant; the primary key IS the tenant id. */
@Entity
@Table(name = "fee_rules")
@Getter
@Setter
@NoArgsConstructor
public class FeeRules {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sibling_concession_type", nullable = false, length = 10)
    private AmountType siblingConcessionType = AmountType.NONE;

    @Column(name = "sibling_concession_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal siblingConcessionValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_fee_type", nullable = false, length = 10)
    private AmountType lateFeeType = AmountType.NONE;

    @Column(name = "late_fee_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal lateFeeValue = BigDecimal.ZERO;

    @Column(name = "late_fee_grace_days", nullable = false)
    private int lateFeeGraceDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    public FeeRules(UUID tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
