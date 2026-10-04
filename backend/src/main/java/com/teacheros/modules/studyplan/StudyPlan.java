package com.teacheros.modules.studyplan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "study_plans")
@Getter
@Setter
@NoArgsConstructor
public class StudyPlan {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "plan_text", nullable = false, columnDefinition = "TEXT")
    private String planText;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public StudyPlan(UUID tenantId, UUID studentId, String planText) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.planText = planText;
        this.generatedAt = Instant.now();
    }
}
