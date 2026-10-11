package com.teacheros.modules.student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_documents")
@Getter
@Setter
@NoArgsConstructor
public class StudentDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // One of StudentProfileService.DOCUMENT_CATEGORIES - a plain string
    // rather than an enum so a new category is a service-level change, not
    // a migration.
    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @Column(nullable = false)
    private String title;

    // The object's key in storage - never the file bytes or a URL.
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
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

    public StudentDocument(UUID tenantId, UUID studentId, String docType, String title, String objectKey,
            String originalFilename, String contentType, long fileSizeBytes, UUID uploadedByUserId) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.docType = docType;
        this.title = title;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedByUserId = uploadedByUserId;
    }
}
