package com.teacheros.modules.student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String grade;

    @Column(name = "guardian_phone", nullable = false)
    private String guardianPhone;

    // Optional - lets the student log in and view their own attendance/fees
    // directly, separately from the guardian_phone their parent uses. Most
    // students won't have this set, especially younger ones - that's fine,
    // it's nullable by design (see V2 migration).
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Student(UUID tenantId, UUID batchId, String fullName, String grade, String guardianPhone) {
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.fullName = fullName;
        this.grade = grade;
        this.guardianPhone = guardianPhone;
    }
}
