package com.teacheros.modules.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "class_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ClassSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public ClassSession(UUID tenantId, UUID batchId, LocalDate sessionDate) {
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.sessionDate = sessionDate;
    }
}
