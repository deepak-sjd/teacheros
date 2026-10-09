package com.teacheros.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class BatchDtos {

    public record CreateBatchRequest(
            @NotBlank String name,
            String subject,
            String scheduleSummary) {
    }

    // Full replace of the editable fields, same as UpdateStudentRequest.
    public record UpdateBatchRequest(
            @NotBlank String name,
            String subject,
            String scheduleSummary) {
    }

    public record BatchResponse(
            UUID id,
            String name,
            String subject,
            String scheduleSummary) {
    }
}
