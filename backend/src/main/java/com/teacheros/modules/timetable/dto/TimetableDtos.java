package com.teacheros.modules.timetable.dto;

import com.teacheros.modules.timetable.MeetingPlatform;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.UUID;

public class TimetableDtos {

    public record CreateSlotRequest(
            @NotNull UUID batchId,
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            String subject,
            @Size(max = 500) String meetingLink,
            MeetingPlatform meetingPlatform) {
    }

    public record SlotResponse(
            UUID id,
            UUID batchId,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String subject,
            String meetingLink,
            MeetingPlatform meetingPlatform) {
    }

    // A blank/null meetingLink clears the link (and platform) entirely -
    // see TimetableService#updateMeetingLink. Kept as its own endpoint/DTO
    // rather than folded into a general "update slot" so setting a link
    // can't accidentally also change the slot's day/time.
    public record UpdateMeetingLinkRequest(
            @Size(max = 500) String meetingLink,
            MeetingPlatform meetingPlatform) {
    }
}
