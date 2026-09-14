package com.teacheros.modules.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One record per (class_session, student). The percentage a teacher/parent
 * sees is NEVER stored anywhere - it is always computed at query time from
 * these rows (present count / total non-cancelled sessions). See
 * AttendanceService#getStudentAttendanceSummary. This is a deliberate
 * design rule: a stored percentage can drift out of sync with the
 * underlying records; a derived one cannot.
 */
@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "class_session_id", nullable = false)
    private UUID classSessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Column(name = "marked_by_user_id", nullable = false)
    private UUID markedByUserId;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    @PrePersist
    void onCreate() {
        if (this.markedAt == null) {
            this.markedAt = Instant.now();
        }
    }

    public Attendance(UUID tenantId, UUID classSessionId, UUID studentId,
                       AttendanceStatus status, UUID markedByUserId) {
        this.tenantId = tenantId;
        this.classSessionId = classSessionId;
        this.studentId = studentId;
        this.status = status;
        this.markedByUserId = markedByUserId;
    }
}
