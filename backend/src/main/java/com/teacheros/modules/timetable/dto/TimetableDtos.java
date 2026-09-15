package com.teacheros.modules.timetable.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

public class TimetableDtos {

    public record CreateSlotRequest(
            @NotNull UUID batchId,
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            String subject) {
    }

    public record SlotResponse(
            UUID id,
            UUID batchId,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String subject) {
    }
}
