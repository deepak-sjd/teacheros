package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instalment_plans")
@Getter
@Setter
@NoArgsConstructor
public class InstalmentPlan {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "number_of_instalments", nullable = false)
    private int numberOfInstalments;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public InstalmentPlan(UUID tenantId, String name, int numberOfInstalments) {
        this.tenantId = tenantId;
        this.name = name;
        this.numberOfInstalments = numberOfInstalments;
    }
}
