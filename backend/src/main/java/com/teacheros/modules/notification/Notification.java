package com.teacheros.modules.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    // Shared read state between whoever views it (parent or the student
    // themself, for the same linked student) - simplification for this
    // phase. If separate per-viewer read state is ever needed, split this
    // into a notification_reads join table keyed by user, not before.
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Notification(UUID tenantId, UUID studentId, NotificationType type, String title, String message) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.type = type;
        this.title = title;
        this.message = message;
    }
}
