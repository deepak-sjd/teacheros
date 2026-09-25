package com.teacheros.modules.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ReportDtos {

    public record BatchAttendanceRow(
            UUID batchId,
            String batchName,
            long sessions,
            long presentCount,
            double attendancePercentage) {
    }

    public record AttendanceSummary(
            long totalSessions,
            long presentCount,
            double attendancePercentage,
            List<BatchAttendanceRow> byBatch) {
    }

    // Tenant-wide, not per-batch - Fee has no batchId (fees are tied to a
    // student, students aren't required to have exactly one batch), so
    // there is no honest way to break this figure down by batch.
    public record FeeSummary(
            BigDecimal collectedInPeriod,
            BigDecimal totalOutstanding,
            long pendingCount,
            long overdueCount) {
    }

    public record BatchMarksRow(
            UUID batchId,
            String batchName,
            long testsConducted,
            long gradedEntries,
            double averageScorePercentage) {
    }

    public record MarksSummary(
            long testsConducted,
            long gradedEntries,
            double averageScorePercentage,
            List<BatchMarksRow> byBatch) {
    }

    public record ReportSummaryResponse(
            LocalDate from,
            LocalDate to,
            UUID batchId,
            AttendanceSummary attendance,
            FeeSummary fees,
            MarksSummary marks) {
    }
}
