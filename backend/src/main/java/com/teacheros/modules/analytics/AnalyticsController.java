package com.teacheros.modules.analytics;

import com.teacheros.modules.analytics.dto.AnalyticsDtos.AttendanceTrendResponse;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.FeeCollectionTrendResponse;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.SubjectPerformanceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Staff-facing dashboard charts - tenant-wide by default, optionally
 * narrowed to one batch. Unlike /reports/summary (a point-in-time table
 * for a chosen range), these are shaped specifically for the two charts on
 * the Overview page: a day-by-day attendance trend and a per-subject
 * average score.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/attendance-trend")
    public ResponseEntity<AttendanceTrendResponse> attendanceTrend(
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) UUID batchId) {
        return ResponseEntity.ok(analyticsService.attendanceTrend(days, batchId));
    }

    @GetMapping("/subject-performance")
    public ResponseEntity<SubjectPerformanceResponse> subjectPerformance(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) UUID batchId) {
        return ResponseEntity.ok(analyticsService.subjectPerformance(days, batchId));
    }

    // No batchId param, unlike the other two - fees have no batchId in the
    // schema, so this is always tenant-wide (see the DTO's javadoc).
    @GetMapping("/fee-collection-trend")
    public ResponseEntity<FeeCollectionTrendResponse> feeCollectionTrend(
            @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(analyticsService.feeCollectionTrend(days));
    }
}
