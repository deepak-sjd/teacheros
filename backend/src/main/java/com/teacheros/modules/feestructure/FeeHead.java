package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_heads")
@Getter
@Setter
@NoArgsConstructor
public class FeeHead {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String code;

    @Column(name = "sibling_concession_eligible", nullable = false)
    private boolean siblingConcessionEligible = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public FeeHead(UUID tenantId, String name, String code, boolean siblingConcessionEligible, int sortOrder) {
        this.tenantId = tenantId;
        this.name = name;
        this.code = code;
        this.siblingConcessionEligible = siblingConcessionEligible;
        this.sortOrder = sortOrder;
    }
}
