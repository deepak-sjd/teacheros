package com.teacheros.modules.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "marks")
@Getter
@Setter
@NoArgsConstructor
public class Mark {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // Nullable - a mark row can exist before grading happens. The service
    // always upserts by (testId, studentId), so "not yet graded" is
    // represented as a NULL value here, not the absence of a row.
    @Column(name = "marks_obtained", precision = 6, scale = 2)
    private BigDecimal marksObtained;

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

    public Mark(UUID tenantId, UUID testId, UUID studentId, BigDecimal marksObtained) {
        this.tenantId = tenantId;
        this.testId = testId;
        this.studentId = studentId;
        this.marksObtained = marksObtained;
    }
}
