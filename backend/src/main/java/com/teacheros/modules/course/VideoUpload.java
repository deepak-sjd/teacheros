package com.teacheros.modules.course;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_uploads")
@Getter
@Setter
@NoArgsConstructor
public class VideoUpload {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 30)
    private String provider;

    // The provider's own id for the video. Never a playable URL.
    @Column(name = "video_ref", nullable = false)
    private String videoRef;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VideoUploadStatus status = VideoUploadStatus.PENDING_UPLOAD;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "failure_reason")
    private String failureReason;

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

    public VideoUpload(UUID tenantId, String provider, String videoRef, String title, long sizeBytes,
            UUID createdByUserId) {
        this.tenantId = tenantId;
        this.provider = provider;
        this.videoRef = videoRef;
        this.title = title;
        this.sizeBytes = sizeBytes;
        this.createdByUserId = createdByUserId;
    }
}
