package com.teacheros.modules.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class HomeworkDtos {

    public record CreateHomeworkRequest(
            @NotNull UUID batchId,
            @NotBlank String title,
            String description,
            @NotNull LocalDate dueDate) {
    }

    public record UpdateHomeworkRequest(
            @NotBlank String title,
            String description,
            @NotNull LocalDate dueDate) {
    }

    public record HomeworkResponse(
            UUID id,
            UUID batchId,
            String title,
            String description,
            LocalDate dueDate,
            Instant createdAt,
            Instant updatedAt) {
    }
}
