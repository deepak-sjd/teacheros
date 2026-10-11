package com.teacheros.modules.homework;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "homework_submissions")
@Getter
@Setter
@NoArgsConstructor
public class HomeworkSubmission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "homework_id", nullable = false)
    private UUID homeworkId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(columnDefinition = "TEXT")
    private String text;

    // Storage object key, never a URL - see StorageService's javadoc.
    @Column(name = "object_key", length = 500)
    private String objectKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(name = "teacher_feedback", columnDefinition = "TEXT")
    private String teacherFeedback;

    @Column(length = 50)
    private String grade;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @PrePersist
    void onCreate() {
        if (this.submittedAt == null) {
            this.submittedAt = Instant.now();
        }
    }

    public HomeworkSubmission(UUID tenantId, UUID homeworkId, UUID studentId) {
        this.tenantId = tenantId;
        this.homeworkId = homeworkId;
        this.studentId = studentId;
    }
}
