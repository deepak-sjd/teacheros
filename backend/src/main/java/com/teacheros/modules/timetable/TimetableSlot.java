package com.teacheros.modules.timetable;

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
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Optional override of the batch's subject - most batches teach one
    // subject so this is usually left null and the batch's subject is used.
    private String subject;

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
