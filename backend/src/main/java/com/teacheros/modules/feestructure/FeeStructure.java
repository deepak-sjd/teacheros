package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_structures")
@Getter
@Setter
@NoArgsConstructor
public class FeeStructure {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    private String grade;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public FeeStructure(UUID tenantId, String name, String academicYear, String grade, UUID batchId, UUID planId) {
        this.tenantId = tenantId;
        this.name = name;
        this.academicYear = academicYear;
        this.grade = grade;
        this.batchId = batchId;
        this.planId = planId;
    }
}
