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

    // Optional - lets low-attendance/fee alerts be emailed to the guardian,
    // in addition to the in-app notification. Nullable by design; most
    // schools will only have the phone number on file.
    @Column(name = "guardian_email")
    private String guardianEmail;

    // Optional - lets the student log in and view their own attendance/fees
    // directly, separately from the guardian_phone their parent uses. Most
    // students won't have this set, especially younger ones - that's fine,
    // it's nullable by design (see V2 migration).
    @Column(name = "phone_number")
    private String phoneNumber;

    // ---- Profile extras (V120). All optional. ----

    @Column(name = "previous_school")
    private String previousSchool;

    // SENSITIVE - never include in toString/logging/audit metadata, never
    // return from list endpoints. See StudentProfileService.
    @Column(name = "medical_notes", columnDefinition = "TEXT")
    private String medicalNotes;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Contact details only. A second guardian phone does NOT grant PARENT
    // login/access - StudentAccessGuard still matches on guardian_phone.
    @Column(name = "guardian2_name")
    private String guardian2Name;

    @Column(name = "guardian2_phone", length = 20)
    private String guardian2Phone;

    @Column(name = "guardian2_relation", length = 50)
    private String guardian2Relation;

    // ---- Academic placement (V30). SCHOOL tenants only; null otherwise. ----
    // Set through modules.academic.StudentPlacementService, not the normal
    // student create/update, so editing a student never clears it.

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "roll_number", length = 20)
    private String rollNumber;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

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
