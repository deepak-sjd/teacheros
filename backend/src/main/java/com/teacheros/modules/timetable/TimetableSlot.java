package com.teacheros.modules.timetable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "timetable_slots")
@Getter
@Setter
@NoArgsConstructor
public class TimetableSlot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    // 1=Monday .. 7=Sunday (ISO-8601, matches java.time.DayOfWeek.getValue())
    // 1=Monday .. 7=Sunday (ISO-8601, matches java.time.DayOfWeek.getValue()).
    // The column is SMALLINT (V5), so say so - otherwise schema validation expects
    // INTEGER.
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Optional override of the batch's subject - most batches teach one
    // subject so this is usually left null and the batch's subject is used.
    private String subject;

    // Optional video-call link for this recurring slot (Zoom/Google Meet/
    // Teams/other). Set/cleared independently of the slot's day/time via
    // TimetableService#updateMeetingLink, not just at creation - a teacher
    // adding or swapping a link shouldn't have to delete and recreate the
    // whole slot.
    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_platform", length = 20)
    private MeetingPlatform meetingPlatform;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public TimetableSlot(UUID tenantId, UUID batchId, int dayOfWeek, LocalTime startTime, LocalTime endTime,
            String subject) {
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subject = subject;
    }
}
