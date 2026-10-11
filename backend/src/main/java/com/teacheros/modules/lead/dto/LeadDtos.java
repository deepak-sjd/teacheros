package com.teacheros.modules.lead.dto;

import com.teacheros.modules.lead.LeadSource;
import com.teacheros.modules.lead.LeadStatus;
import com.teacheros.modules.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeadDtos {

    // ---- Staff side ----

    public record LeadResponse(
            UUID id,
            String name,
            String phone,
            String email,
            String message,
            LeadSource source,
            LeadStatus status,
            UUID assignedTo,
            String assignedToName,
            LocalDate nextFollowUp,
            UUID convertedStudentId,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** fromStatus == toStatus means a note-only entry (assignment / follow-up). */
    public record LeadHistoryEntry(
            UUID id,
            LeadStatus fromStatus,
            LeadStatus toStatus,
            String note,
            String changedByName,
            Instant changedAt) {
    }

    public record LeadDetailResponse(LeadResponse lead, List<LeadHistoryEntry> history) {
    }

    /** Every LeadStatus is present, zero-filled. */
    public record LeadCountsResponse(Map<LeadStatus, Long> counts) {
    }

    public record AssigneeResponse(UUID id, String displayName, Role role) {
    }

    /** userId null = unassign. */
    public record AssignLeadRequest(UUID userId) {
    }

    public record UpdateLeadStatusRequest(
            @NotNull LeadStatus status,
            @Size(max = 500) String note) {
    }

    public record FollowUpRequest(
            @NotNull LocalDate nextFollowUp,
            @Size(max = 500) String note) {
    }

    /** studentName blank = use the lead's name. */
    public record ConvertLeadRequest(
            @Size(max = 255) String studentName,
            @Size(max = 50) String grade,
            UUID batchId) {
    }

    public record ConvertLeadResponse(LeadResponse lead, UUID studentId) {
    }

    // ---- Public side ----

    /**
     * schoolSlug is the PUBLIC identifier of a school, not a tenant id.
     * website is a honeypot: real users never see or fill it.
     */
    public record EnquiryRequest(
            @NotBlank @Size(max = 80) String schoolSlug,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 2000) String message,
            LeadSource source,
            UUID courseId,
            @Size(max = 200) String website) {
    }
}
