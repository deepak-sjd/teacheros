package com.teacheros.modules.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_attempts")
@Getter
@Setter
@NoArgsConstructor
public class TestAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    // Null until submitted. Mirrors marks.marks_obtained's nullability
    // reasoning - this row can exist (attempt started) before a score
    // exists.
    @Column(precision = 6, scale = 2)
    private BigDecimal score;

    public TestAttempt(UUID tenantId, UUID testId, UUID studentId, Instant startedAt) {
        this.tenantId = tenantId;
        this.testId = testId;
        this.studentId = studentId;
        this.startedAt = startedAt;
    }
}
