package com.teacheros.modules.report;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.AssessmentRepository;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.assessment.MarkRepository.MarkScore;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeePayment;
import com.teacheros.modules.fee.FeePaymentRepository;
import com.teacheros.modules.fee.FeeRepository;
import com.teacheros.modules.fee.FeeStatus;
import com.teacheros.modules.report.dto.ReportDtos.AttendanceSummary;
import com.teacheros.modules.report.dto.ReportDtos.BatchAttendanceRow;
import com.teacheros.modules.report.dto.ReportDtos.BatchMarksRow;
import com.teacheros.modules.report.dto.ReportDtos.FeeSummary;
import com.teacheros.modules.report.dto.ReportDtos.MarksSummary;
import com.teacheros.modules.report.dto.ReportDtos.ReportSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Reports are computed on demand from data that already exists - same rule
 * this codebase already follows for attendance percentages (see
 * Attendance's class javadoc): nothing here is stored, so a report can
 * never drift out of sync with the records it summarizes. This does mean a
 * report over a long range on a large tenant does real work every time
 * it's requested; there's no caching here, deliberately, since staff-side
 * usage (a teacher pulling a weekly/monthly report) doesn't need it yet.
 */
@Service
public class ReportService {

    private final CurrentTenantProvider currentTenantProvider;
    private final BatchRepository batchRepository;
    private final AttendanceRepository attendanceRepository;
    private final FeeRepository feeRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final AssessmentRepository assessmentRepository;
    private final MarkRepository markRepository;

    public ReportService(
            CurrentTenantProvider currentTenantProvider,
            BatchRepository batchRepository,
            AttendanceRepository attendanceRepository,
            FeeRepository feeRepository,
            FeePaymentRepository feePaymentRepository,
            AssessmentRepository assessmentRepository,
            MarkRepository markRepository) {
        this.currentTenantProvider = currentTenantProvider;
        this.batchRepository = batchRepository;
        this.attendanceRepository = attendanceRepository;
        this.feeRepository = feeRepository;
        this.feePaymentRepository = feePaymentRepository;
        this.assessmentRepository = assessmentRepository;
        this.markRepository = markRepository;
    }

    public ReportSummaryResponse generate(LocalDate from, LocalDate to, UUID batchId) {
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "\"from\" must not be after \"to\"");
        }
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        List<Batch> batches = batchId != null
                ? batchRepository.findByIdAndTenantId(batchId, tenantId)
                        .map(List::of)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found"))
                : batchRepository.findAllByTenantIdAndActiveTrue(tenantId);

        AttendanceSummary attendance = buildAttendanceSummary(tenantId, batches, from, to);
        MarksSummary marks = buildMarksSummary(tenantId, batches, from, to);
        FeeSummary fees = buildFeeSummary(tenantId, from, to);

        return new ReportSummaryResponse(from, to, batchId, attendance, fees, marks);
    }

    private AttendanceSummary buildAttendanceSummary(UUID tenantId, List<Batch> batches, LocalDate from, LocalDate to) {
        List<BatchAttendanceRow> rows = batches.stream()
                .map(batch -> {
                    long sessions = attendanceRepository.countRecordedInRange(tenantId, batch.getId(), from, to);
                    long present = attendanceRepository.countAttendedInRange(tenantId, batch.getId(), from, to);
                    return new BatchAttendanceRow(batch.getId(), batch.getName(), sessions, present,
                            percentage(present, sessions));
                })
                .toList();

        long totalSessions = rows.stream().mapToLong(BatchAttendanceRow::sessions).sum();
        long totalPresent = rows.stream().mapToLong(BatchAttendanceRow::presentCount).sum();

        return new AttendanceSummary(totalSessions, totalPresent, percentage(totalPresent, totalSessions), rows);
    }

    private MarksSummary buildMarksSummary(UUID tenantId, List<Batch> batches, LocalDate from, LocalDate to) {
        List<BatchMarksRow> rows = batches.stream()
                .map(batch -> {
                    long testsConducted = assessmentRepository.countByTenantIdAndBatchIdAndTestDateBetween(
                            tenantId, batch.getId(), from, to);
                    List<MarkScore> scores = markRepository.findScoresInRange(tenantId, batch.getId(), from, to);
                    return new BatchMarksRow(batch.getId(), batch.getName(), testsConducted, scores.size(),
                            averageScorePercentage(scores));
                })
                .toList();

        long testsConducted = rows.stream().mapToLong(BatchMarksRow::testsConducted).sum();
        long gradedEntries = rows.stream().mapToLong(BatchMarksRow::gradedEntries).sum();
        double weightedAverage = rows.stream()
                .mapToDouble(r -> r.averageScorePercentage() * r.gradedEntries())
                .sum();
        double overallAverage = gradedEntries == 0 ? 0.0 : round1(weightedAverage / gradedEntries);

        return new MarksSummary(testsConducted, gradedEntries, overallAverage, rows);
    }

    // Fee has no batchId (a fee is tied to a student, not to a batch), so
    // this figure is always tenant-wide - see FeeSummary's javadoc.
    private FeeSummary buildFeeSummary(UUID tenantId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal collected = feePaymentRepository.sumCollectedBetween(tenantId, fromInstant, toInstant);

        List<Fee> outstandingFees = feeRepository.findAllByTenantIdAndStatusIn(tenantId,
                List.of(FeeStatus.PENDING, FeeStatus.PARTIALLY_PAID, FeeStatus.OVERDUE));

        BigDecimal totalOutstanding = BigDecimal.ZERO;
        long overdueCount = 0;
        long pendingCount = 0;
        for (Fee fee : outstandingFees) {
            BigDecimal paid = feePaymentRepository.findByTenantIdAndFeeId(tenantId, fee.getId()).stream()
                    .map(FeePayment::getAmountPaid)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalOutstanding = totalOutstanding.add(fee.getAmountDue().subtract(paid));

            if (fee.getStatus() == FeeStatus.OVERDUE) {
                overdueCount++;
            } else {
                pendingCount++;
            }
        }

        return new FeeSummary(collected, totalOutstanding, pendingCount, overdueCount);
    }

    private double averageScorePercentage(List<MarkScore> scores) {
        if (scores.isEmpty()) {
            return 0.0;
        }
        double sum = scores.stream()
                .mapToDouble(s -> s.getMarksObtained().doubleValue() / s.getMaxMarks().doubleValue() * 100)
                .sum();
        return round1(sum / scores.size());
    }

    private double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : round1(numerator * 100.0 / denominator);
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
