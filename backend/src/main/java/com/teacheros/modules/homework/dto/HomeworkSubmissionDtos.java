package com.teacheros.modules.homework.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class HomeworkSubmissionDtos {

    /**
     * One shape for every read (student's own, parent's view of a child,
     * and one row of the teacher's list). status is SUBMITTED | REVIEWED |
     * NOT_SUBMITTED - the last one means no row exists yet, in which case
     * id/text/file/feedback fields are all null (same convention as
     * AttemptDtos.MyAttemptResponse).
     *
     * submissionsOpen is computed server-side (Asia/Kolkata, end of the
     * due date) so the client never has to guess at time zones.
     */
    public record SubmissionResponse(
            UUID id,
            UUID homeworkId,
            UUID studentId,
            String studentName,
            String status,
            String text,
            String originalFilename,
            Long fileSizeBytes,
            Instant submittedAt,
            String teacherFeedback,
            String grade,
            Instant reviewedAt,
            boolean submissionsOpen) {
    }

    // At least one of the two must be non-blank - enforced in the service.
    public record ReviewSubmissionRequest(
            @Size(max = 2000) String feedback,
            @Size(max = 50) String grade) {
    }
}
