package com.teacheros.modules.attendance;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.attendance.dto.AttendanceDtos.AttendanceSummaryResponse;
import com.teacheros.modules.attendance.dto.AttendanceDtos.BatchSessionSummary;
import com.teacheros.modules.attendance.dto.AttendanceDtos.MarkAttendanceRequest;
import com.teacheros.modules.attendance.dto.AttendanceDtos.StudentAttendanceEntry;
import com.teacheros.modules.attendance.dto.AttendanceDtos.StudentAttendanceRecord;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Attendance marking and the derived percentage calculation.
 *
 * Design rule (see Attendance entity javadoc): attendance percentage is
 * NEVER stored. It's always computed from the underlying Attendance rows
 * at query time. This is slightly more work per read, but guarantees the
 * number a parent sees can never drift from the actual marked records.
 */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository classSessionRepository;
    private final BatchRepository batchRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final StudentAccessGuard studentAccessGuard;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            ClassSessionRepository classSessionRepository,
            BatchRepository batchRepository,
            CurrentTenantProvider currentTenantProvider,
            StudentAccessGuard studentAccessGuard) {
        this.attendanceRepository = attendanceRepository;
        this.classSessionRepository = classSessionRepository;
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.studentAccessGuard = studentAccessGuard;
    }

    /**
     * Marks (or re-marks) attendance for every student listed, for one
     * batch/date. Idempotent: calling this again for the same batch+date
     * updates existing records rather than creating duplicates, so a
     * teacher correcting a mistake doesn't need a separate "edit" flow.
     */
    @Transactional
    public List<StudentAttendanceRecord> markAttendance(MarkAttendanceRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        // Confirm the batch actually belongs to this tenant before anything
        // else - this is the tenant-isolation check for this endpoint.
        batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND",
                        "Batch not found"));

        ClassSession session = classSessionRepository
                .findByTenantIdAndBatchIdAndSessionDate(tenantId, request.batchId(), request.sessionDate())
                .orElseGet(() -> classSessionRepository.save(
                        new ClassSession(tenantId, request.batchId(), request.sessionDate())));

        return request.entries().stream()
                .map(entry -> upsertAttendance(tenantId, session.getId(), entry, userId))
                .map(a -> new StudentAttendanceRecord(a.getStudentId(), a.getStatus()))
                .toList();
    }

    private Attendance upsertAttendance(UUID tenantId, UUID sessionId, StudentAttendanceEntry entry, UUID userId) {
        Attendance attendance = attendanceRepository
                .findByTenantIdAndClassSessionIdAndStudentId(tenantId, sessionId, entry.studentId())
                .orElseGet(() -> new Attendance(tenantId, sessionId, entry.studentId(), entry.status(), userId));

        attendance.setStatus(entry.status());
        attendance.setMarkedByUserId(userId);
        attendance.setMarkedAt(java.time.Instant.now());
        return attendanceRepository.save(attendance);
    }

    /**
     * The core derived-percentage read. Used by both the teacher app
     * (student detail screen) and the parent/student self-service views -
     * studentAccessGuard is what makes it safe to expose to those roles.
     */
    public AttendanceSummaryResponse getStudentAttendanceSummary(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        long recorded = attendanceRepository.countRecordedSessions(tenantId, studentId);
        long attended = attendanceRepository.countAttendedSessions(tenantId, studentId);
        double percentage = AttendancePercentageCalculator.calculate(recorded, attended);

        return new AttendanceSummaryResponse(studentId, recorded, attended, percentage);
    }

    public List<StudentAttendanceRecord> getSessionAttendance(UUID classSessionId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return attendanceRepository.findByTenantIdAndClassSessionId(tenantId, classSessionId).stream()
                .map(a -> new StudentAttendanceRecord(a.getStudentId(), a.getStatus()))
                .toList();
    }

    /**
     * Attendance history for a batch's detail page - one row per session,
     * newest first, with a quick present/absent/late tally so a teacher can
     * scan for patterns without opening every session individually.
     */
    public List<BatchSessionSummary> listSessionsForBatch(UUID batchId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found"));

        List<ClassSession> sessions = classSessionRepository
                .findByTenantIdAndBatchIdOrderBySessionDateDesc(tenantId, batchId);

        return sessions.stream().map(session -> {
            List<Attendance> records = attendanceRepository
                    .findByTenantIdAndClassSessionId(tenantId, session.getId());

            int present = 0;
            int absent = 0;
            int late = 0;
            for (Attendance record : records) {
                switch (record.getStatus()) {
                    case PRESENT -> present++;
                    case ABSENT -> absent++;
                    case LATE -> late++;
                }
            }

            return new BatchSessionSummary(session.getId(), session.getSessionDate(),
                    present, absent, late, records.size());
        }).toList();
    }

}
