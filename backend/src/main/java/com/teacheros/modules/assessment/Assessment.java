package com.teacheros.modules.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Named Assessment, not Test - Maven Surefire's default test-discovery
 * pattern matches any class named "Test*.java" or "*Test.java" and would
 * try to run this (and TestController, TestService, etc.) as a JUnit test
 * class. The API and UI still say "test" everywhere; this is purely an
 * internal naming choice to avoid confusing the build tooling.
 */
@Entity
@Table(name = "tests")
@Getter
@Setter
@NoArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private String title;

    private String subject;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    // OFFLINE (default) keeps the original behaviour - teacher enters
    // marks manually. ONLINE_MCQ additionally has TestQuestion rows and
    // is taken by students in-app; see V11 migration comment for the
    // full reasoning.
    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false)
    private TestType testType = TestType.OFFLINE;

    // Advisory client-side countdown only for ONLINE_MCQ - see V11.
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "available_from")
    private Instant availableFrom;

    @Column(name = "available_until")
    private Instant availableUntil;

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

    public Assessment(UUID tenantId, UUID batchId, String title, String subject, BigDecimal maxMarks,
            LocalDate testDate, UUID createdByUserId) {
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.title = title;
        this.subject = subject;
        this.maxMarks = maxMarks;
        this.testDate = testDate;
        this.createdByUserId = createdByUserId;
    }
}
