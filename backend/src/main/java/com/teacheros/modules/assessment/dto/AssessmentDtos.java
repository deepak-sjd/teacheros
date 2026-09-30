package com.teacheros.modules.assessment.dto;

import com.teacheros.modules.assessment.TestType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class AssessmentDtos {

    public record CreateAssessmentRequest(
            @NotNull UUID batchId,
            @NotBlank String title,
            String subject,
            // For ONLINE_MCQ this is just a placeholder - it's overwritten
            // by ReplaceQuestionsRequest with the sum of question marks as
            // soon as questions are saved. Still required here so the
            // column's NOT NULL constraint is always satisfied.
            @NotNull @DecimalMin(value = "0.01") BigDecimal maxMarks,
            @NotNull LocalDate testDate,
            // Null defaults to OFFLINE in the service - existing callers
            // that don't know about this field keep working unchanged.
            TestType testType,
            Integer durationMinutes,
            Instant availableFrom,
            Instant availableUntil) {
    }

    public record UpdateAssessmentRequest(
            @NotBlank String title,
            String subject,
            @NotNull @DecimalMin(value = "0.01") BigDecimal maxMarks,
            @NotNull LocalDate testDate) {
    }

    public record AssessmentResponse(
            UUID id,
            UUID batchId,
            String title,
            String subject,
            BigDecimal maxMarks,
            LocalDate testDate,
            TestType testType,
            Integer durationMinutes,
            Instant availableFrom,
            Instant availableUntil,
            Instant createdAt,
            Instant updatedAt) {
    }
}
