package com.teacheros.modules.reportcard;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exam_terms")
@Getter
@Setter
@NoArgsConstructor
public class ExamTerm {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // No FK yet - see V80 migration comment.
    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public ExamTerm(UUID tenantId, UUID academicYearId, String name, LocalDate startDate, LocalDate endDate) {
        this.tenantId = tenantId;
        this.academicYearId = academicYearId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
