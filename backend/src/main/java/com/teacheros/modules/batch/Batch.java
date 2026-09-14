package com.teacheros.modules.batch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
public class Batch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private String subject;

    @Column(name = "schedule_summary")
    private String scheduleSummary;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Batch(UUID tenantId, String name, String subject, String scheduleSummary) {
        this.tenantId = tenantId;
        this.name = name;
        this.subject = subject;
        this.scheduleSummary = scheduleSummary;
    }
}
