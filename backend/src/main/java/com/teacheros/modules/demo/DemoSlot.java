package com.teacheros.modules.demo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_slots")
@Getter
@Setter
@NoArgsConstructor
public class DemoSlot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    @Column(nullable = false)
    private int capacity;

    @Column(length = 255)
    private String location;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public DemoSlot(UUID tenantId, Instant startsAt, int durationMinutes, int capacity) {
        this.tenantId = tenantId;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
        this.capacity = capacity;
    }
}
