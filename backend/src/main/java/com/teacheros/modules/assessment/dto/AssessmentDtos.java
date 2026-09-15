package com.teacheros.modules.assessment.dto;

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
            @NotNull @DecimalMin(value = "0.01") BigDecimal maxMarks,
            @NotNull LocalDate testDate) {
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
            Instant createdAt,
            Instant updatedAt) {
    }
}
