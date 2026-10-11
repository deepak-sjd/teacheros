package com.teacheros.modules.announcement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnnouncementAudience audience;

    // Set only when audience == BATCH
    @Column(name = "batch_id")
    private UUID batchId;

    // Set only when audience == CLASS (matched against students.grade)
    @Column(length = 50)
    private String grade;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_by_name", nullable = false)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Announcement(UUID tenantId, String title, String body, AnnouncementAudience audience, UUID batchId,
            String grade, UUID createdByUserId, String createdByName) {
        this.tenantId = tenantId;
        this.title = title;
        this.body = body;
        this.audience = audience;
        this.batchId = batchId;
        this.grade = grade;
        this.createdByUserId = createdByUserId;
        this.createdByName = createdByName;
    }
}
