package com.teacheros.modules.analytics;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.AttendanceTrendPoint;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.AttendanceTrendResponse;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.SubjectPerformancePoint;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.SubjectPerformanceResponse;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.assessment.MarkRepository.SubjectScoreRow;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.attendance.AttendanceRepository.AttendanceRecordRow;
import com.teacheros.modules.attendance.AttendanceStatus;
import com.teacheros.modules.batch.BatchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Dashboard charts for staff - both series are computed on demand from
 * attendance/mark records that already exist, the same "never store a
 * derived number" rule ReportService follows (see its javadoc). Nothing
 * here is cached, so a chart can never drift out of sync with the
 * underlying records; it just costs a query every time it's requested,
 * which is fine for the once-per-dashboard-load usage this serves.
 */
@Service
public class AnalyticsService {

    private final CurrentTenantProvider currentTenantProvider;
    private final AttendanceRepository attendanceRepository;
    private final MarkRepository markRepository;
    private final BatchRepository batchRepository;

    public AnalyticsService(
            CurrentTenantProvider currentTenantProvider,
            AttendanceRepository attendanceRepository,
            MarkRepository markRepository,
            BatchRepository batchRepository) {
        this.currentTenantProvider = currentTenantProvider;
        this.attendanceRepository = attendanceRepository;
        this.markRepository = markRepository;
        this.batchRepository = batchRepository;
    }

    /**
     * One point per calendar day that actually had recorded sessions in
     * range - days with no classes held are omitted rather than plotted as
     * a false "0%", since that would misrepresent a day off as a bad
     * attendance day.
     */
    public AttendanceTrendResponse attendanceTrend(int days, UUID batchId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(days, 1) - 1L);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        assertBatchBelongsToTenant(tenantId, batchId);

        List<AttendanceRecordRow> rows = attendanceRepository.findRecordsInRange(tenantId, batchId, from, to);

        // TreeMap so iteration below comes out chronologically, oldest
        // first - what a trend line needs, without a separate sort step.
        Map<LocalDate, long[]> byDate = new TreeMap<>();
        for (AttendanceRecordRow row : rows) {
            long[] counts = byDate.computeIfAbsent(row.getSessionDate(), d -> new long[2]); // [recorded, attended]
            counts[0]++;
            if (row.getStatus() == AttendanceStatus.PRESENT || row.getStatus() == AttendanceStatus.LATE) {
                counts[1]++;
            }
        }

        List<AttendanceTrendPoint> points = byDate.entrySet().stream()
                .map(e -> new AttendanceTrendPoint(e.getKey(), e.getValue()[0], e.getValue()[1],
                        percentage(e.getValue()[1], e.getValue()[0])))
                .toList();

        return new AttendanceTrendResponse(from, to, batchId, points);
    }

    /**
     * Average score percentage per subject, resolving each test's subject
     * the same way Assessment.subject's own javadoc describes: the
     * assessment's override if set, else its batch's subject, else
     * "General" if neither is set. Sorted alphabetically so the chart's
     * bar order doesn't jump around between requests.
     */
    public SubjectPerformanceResponse subjectPerformance(int days, UUID batchId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(days, 1) - 1L);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        assertBatchBelongsToTenant(tenantId, batchId);

        List<SubjectScoreRow> rows = markRepository.findSubjectScoresInRange(tenantId, batchId, from, to);

        Map<String, List<Double>> percentagesBySubject = new LinkedHashMap<>();
        for (SubjectScoreRow row : rows) {
            String subject = resolveSubject(row.getAssessmentSubject(), row.getBatchSubject());
            double pct = row.getMarksObtained().doubleValue() / row.getMaxMarks().doubleValue() * 100;
            percentagesBySubject.computeIfAbsent(subject, s -> new ArrayList<>()).add(pct);
        }

        List<SubjectPerformancePoint> points = percentagesBySubject.entrySet().stream()
                .map(e -> new SubjectPerformancePoint(e.getKey(), e.getValue().size(), average(e.getValue())))
                .sorted(Comparator.comparing(SubjectPerformancePoint::subject))
                .toList();

        return new SubjectPerformanceResponse(from, to, batchId, points);
    }

    private String resolveSubject(String assessmentSubject, String batchSubject) {
        if (assessmentSubject != null && !assessmentSubject.isBlank()) {
            return assessmentSubject;
        }
        if (batchSubject != null && !batchSubject.isBlank()) {
            return batchSubject;
        }
        return "General";
    }

    private void assertBatchBelongsToTenant(UUID tenantId, UUID batchId) {
        if (batchId == null) {
            return;
        }
        batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found"));
    }

    private double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : round1(numerator * 100.0 / denominator);
    }

    private double average(List<Double> values) {
        return round1(values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
