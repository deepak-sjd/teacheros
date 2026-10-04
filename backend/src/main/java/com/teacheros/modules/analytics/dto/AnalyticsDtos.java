package com.teacheros.modules.analytics.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AnalyticsDtos {

    public record AttendanceTrendPoint(
            LocalDate date,
            long sessionsRecorded,
            long sessionsAttended,
            double attendancePercentage) {
    }

    public record AttendanceTrendResponse(
            LocalDate from,
            LocalDate to,
            UUID batchId,
            List<AttendanceTrendPoint> points) {
    }

    public record SubjectPerformancePoint(
            String subject,
            long gradedEntries,
            double averagePercentage) {
    }

    public record SubjectPerformanceResponse(
            LocalDate from,
            LocalDate to,
            UUID batchId,
            List<SubjectPerformancePoint> points) {
    }

    public record FeeCollectionPoint(
            LocalDate date,
            java.math.BigDecimal amountCollected) {
    }

    // No batchId field, unlike the other two trends - Fee has no batchId in
    // the schema (see ReportDtos.FeeReportSummary's javadoc), so this is
    // always tenant-wide.
    public record FeeCollectionTrendResponse(
            LocalDate from,
            LocalDate to,
            List<FeeCollectionPoint> points) {
    }
}
