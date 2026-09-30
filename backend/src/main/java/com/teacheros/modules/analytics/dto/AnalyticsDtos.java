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
}
