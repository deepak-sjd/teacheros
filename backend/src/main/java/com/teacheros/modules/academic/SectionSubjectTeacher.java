package com.teacheros.modules.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "section_subject_teachers")
@Getter
@Setter
@NoArgsConstructor
public class SectionSubjectTeacher {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_user_id", nullable = false)
    private UUID teacherUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public SectionSubjectTeacher(UUID tenantId, UUID sectionId, UUID subjectId, UUID teacherUserId) {
        this.tenantId = tenantId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.teacherUserId = teacherUserId;
    }
}
