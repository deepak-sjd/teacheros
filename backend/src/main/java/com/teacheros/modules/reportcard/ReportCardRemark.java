package com.teacheros.modules.reportcard;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_card_remarks")
@Getter
@Setter
@NoArgsConstructor
public class ReportCardRemark {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(nullable = false, length = 1000)
    private String remark;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

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

    public ReportCardRemark(UUID tenantId, UUID studentId, UUID examTermId, String remark, UUID createdByUserId) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.examTermId = examTermId;
        this.remark = remark;
        this.createdByUserId = createdByUserId;
    }
}
