package com.teacheros.modules.leave.dto;

import com.teacheros.modules.leave.LeaveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class LeaveRequestDtos {

    public record SubmitLeaveRequest(
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotBlank @Size(max = 500) String reason) {

        // The reason can contain health details - keep it out of any log line.
        @Override
        public String toString() {
            return "SubmitLeaveRequest[fromDate=" + fromDate + ", toDate=" + toDate + ", reason=<redacted>]";
        }
    }

    // decision is a String (not the enum) so a bad value is a clean 400
    // validation error instead of an unhandled JSON-parse failure.
    // note is required when rejecting - enforced in the service.
    public record DecideLeaveRequest(
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED", message = "must be APPROVED or REJECTED") String decision,
            @Size(max = 500) String note) {
    }

    public record LeaveRequestResponse(
            UUID id,
            UUID studentId,
            String studentName,
            LocalDate fromDate,
            LocalDate toDate,
            String reason,
            LeaveStatus status,
            Instant createdAt,
            Instant decidedAt,
            String decisionNote) {

        @Override
        public String toString() {
            return "LeaveRequestResponse[id=" + id + ", status=" + status + ", reason=<redacted>]";
        }
    }
}
