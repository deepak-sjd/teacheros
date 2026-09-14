package com.teacheros.modules.material;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "study_material")
@Getter
@Setter
@NoArgsConstructor
public class StudyMaterial {

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

    // The object's path/key in storage (MinIO locally, S3/R2/Spaces in
    // production) - never the file bytes themselves. E.g.
    // "tenants/{tenantId}/batches/{batchId}/{uuid}-{filename}".
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public StudyMaterial(UUID tenantId, UUID batchId, String title, String description, String objectKey,
            String originalFilename, String contentType, long fileSizeBytes, UUID uploadedByUserId) {
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.title = title;
        this.description = description;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedByUserId = uploadedByUserId;
    }
}
