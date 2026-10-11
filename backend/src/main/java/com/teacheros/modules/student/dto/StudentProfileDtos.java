package com.teacheros.modules.student.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Kept separate from StudentDtos on purpose: StudentResponse is what every
 * list endpoint returns, and medical notes must never ride along on those.
 * Existing create/update student requests are unchanged, so existing
 * clients keep working with the new fields simply absent.
 */
public class StudentProfileDtos {

    // Full replace of the profile extras, same style as UpdateStudentRequest.
    // Blank strings are normalised to null in the service. toString is
    // overridden so medical notes can't leak if a request is ever logged.
    public record UpdateStudentProfileRequest(
            @Size(max = 255) String previousSchool,
            @Size(max = 2000) String medicalNotes,
            @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Blood group must look like A+, O-, AB+") String bloodGroup,
            @Size(max = 1000) String address,
            @Size(max = 255) String guardian2Name,
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid phone number") String guardian2Phone,
            @Size(max = 50) String guardian2Relation) {

        @Override
        public String toString() {
            return "UpdateStudentProfileRequest[medicalNotes=<redacted>]";
        }
    }

    /**
     * medicalNotes is null unless the caller is staff or the student's own
     * guardian (PARENT). medicalNotesVisible tells the UI whether to render
     * the field at all, so "no notes recorded" and "you can't see notes"
     * aren't confused.
     */
    public record StudentProfileResponse(
            UUID studentId,
            String previousSchool,
            String medicalNotes,
            boolean medicalNotesVisible,
            String bloodGroup,
            String address,
            String guardian2Name,
            String guardian2Phone,
            String guardian2Relation) {

        @Override
        public String toString() {
            return "StudentProfileResponse[studentId=" + studentId + ", medicalNotes=<redacted>]";
        }
    }

    public record StudentDocumentResponse(
            UUID id,
            UUID studentId,
            String docType,
            String title,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Instant createdAt) {
    }

    public record DocumentDownloadUrlResponse(
            String url,
            int expiresInMinutes) {
    }
}
