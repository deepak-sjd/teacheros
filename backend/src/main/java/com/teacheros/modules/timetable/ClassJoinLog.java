package com.teacheros.modules.timetable;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "class_join_logs")
@Getter
@Setter
@NoArgsConstructor
public class ClassJoinLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "timetable_slot_id", nullable = false)
    private UUID timetableSlotId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // The calendar date of the session in Asia/Kolkata, not a UTC date.
    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }

    public ClassJoinLog(UUID tenantId, UUID timetableSlotId, UUID studentId, LocalDate sessionDate) {
        this.tenantId = tenantId;
        this.timetableSlotId = timetableSlotId;
        this.studentId = studentId;
        this.sessionDate = sessionDate;
    }
}
