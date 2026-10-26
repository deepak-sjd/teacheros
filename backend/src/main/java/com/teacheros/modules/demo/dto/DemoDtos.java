package com.teacheros.modules.demo.dto;

import com.teacheros.modules.demo.DemoBookingStatus;
import com.teacheros.modules.lead.LeadStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class DemoDtos {

    // ---- Staff ----

    /** courseId and batchId are both optional; sending both is rejected. */
    public record CreateSlotRequest(
            UUID courseId,
            UUID batchId,
            @NotNull @Future Instant startsAt,
            @Min(5) @Max(480) int durationMinutes,
            @Min(1) @Max(500) int capacity,
            @Size(max = 255) String location) {
    }

    public record SlotResponse(
            UUID id,
            UUID courseId,
            String courseTitle,
            UUID batchId,
            Instant startsAt,
            int durationMinutes,
            int capacity,
            long booked,
            long remaining,
            String location,
            boolean cancelled) {
    }

    public record BookingResponse(
            UUID id,
            UUID slotId,
            Instant slotStartsAt,
            UUID leadId,
            String leadName,
            String leadPhone,
            LeadStatus leadStatus,
            DemoBookingStatus status,
            Instant createdAt) {
    }

    public record BookLeadRequest(@NotNull UUID slotId) {
    }

    /** Only SHOWED or NO_SHOW are accepted. */
    public record MarkAttendanceRequest(@NotNull DemoBookingStatus status) {
    }

    // ---- Public (no booking list, no lead data; only what a visitor needs) ----

    public record PublicDemoSlot(
            UUID id,
            Instant startsAt,
            int durationMinutes,
            String location,
            long remaining,
            String courseTitle) {
    }

    /** website is a honeypot: real users never see or fill it. */
    public record PublicDemoBookingRequest(
            @NotBlank @Size(max = 80) String schoolSlug,
            @NotNull UUID slotId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 200) String website) {
    }

    public record PublicDemoBookingResponse(Instant startsAt, int durationMinutes, String location) {
    }
}
