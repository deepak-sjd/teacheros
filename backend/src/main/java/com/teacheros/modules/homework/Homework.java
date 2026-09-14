package com.teacheros.modules.homework;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "homework")
@Getter
@Setter
@NoArgsConstructor
public class Homework {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

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

    public Homework(UUID tenantId, UUID batchId, String title, String description, LocalDate dueDate,
            UUID createdByUserId) {
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.createdByUserId = createdByUserId;
    }
}
