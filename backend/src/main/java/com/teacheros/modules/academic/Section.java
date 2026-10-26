package com.teacheros.modules.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sections")
@Getter
@Setter
@NoArgsConstructor
public class Section {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "class_teacher_user_id")
    private UUID classTeacherUserId;

    // Null = no limit. A soft limit: checked when placing a student, not
    // locked, so two simultaneous placements can overshoot by one.
    private Integer capacity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Section(UUID tenantId, UUID classId, String name) {
        this.tenantId = tenantId;
        this.classId = classId;
        this.name = name;
    }
}
