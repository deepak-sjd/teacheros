package com.teacheros.modules.attendance.dto;

import com.teacheros.modules.attendance.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AttendanceDtos {

    /**
     * Marks attendance for an entire batch's session in one call - matches
     * the actual teacher workflow ("mark today's roster"), not one API call
     * per student.
     */
    public record MarkAttendanceRequest(
            @NotNull UUID batchId,
            @NotNull LocalDate sessionDate,
            @NotEmpty @Valid List<StudentAttendanceEntry> entries) {
    }

    public record StudentAttendanceEntry(
            @NotNull UUID studentId,
            @NotNull AttendanceStatus status) {
    }

    public record StudentAttendanceRecord(
            UUID studentId,
            AttendanceStatus status) {
    }

    public record AttendanceSummaryResponse(
            UUID studentId,
            long sessionsRecorded,
            long sessionsAttended,
            double attendancePercentage) {
    }

    /**
     * One row in a batch's attendance history list - a session date plus
     * how many students landed in each status. Deliberately doesn't include
     * per-student detail here (that's a separate call to the existing
     * GET /attendance/sessions/{id} endpoint if a teacher taps into one
     * session) - keeps the history list itself lightweight.
     *
     * totalCount is every record in the session including excused ones, so
     * present + absent + late + excused == totalCount.
     */
    public record BatchSessionSummary(
            UUID classSessionId,
            LocalDate sessionDate,
            int presentCount,
            int absentCount,
            int lateCount,
            int excusedCount,
            int totalCount) {
    }

    /** Teacher-side: request a QR token to display for a batch's session. */
    public record QrTokenRequest(
            @NotNull UUID batchId,
            @NotNull LocalDate sessionDate) {
    }

    public record QrTokenResponse(
            String token,
            java.time.Instant expiresAt) {
    }

    /** Student-side: what they submit after scanning the teacher's QR code. */
    public record QrCheckInRequest(
            @NotNull String token) {
    }
}
