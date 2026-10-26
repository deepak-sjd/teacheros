package com.teacheros.modules.demo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_bookings")
@Getter
@Setter
@NoArgsConstructor
public class DemoBooking {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemoBookingStatus status = DemoBookingStatus.BOOKED;

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

    public DemoBooking(UUID tenantId, UUID slotId, UUID leadId) {
        this.tenantId = tenantId;
        this.slotId = slotId;
        this.leadId = leadId;
    }
}
