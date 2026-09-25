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
    private final AttendanceQrTokenService qrTokenService;
    private final com.teacheros.modules.student.StudentRepository studentRepository;
    private final com.teacheros.modules.user.UserRepository userRepository;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            ClassSessionRepository classSessionRepository,
            BatchRepository batchRepository,
            CurrentTenantProvider currentTenantProvider,
            StudentAccessGuard studentAccessGuard,
            AttendanceQrTokenService qrTokenService,
            com.teacheros.modules.student.StudentRepository studentRepository,
            com.teacheros.modules.user.UserRepository userRepository) {
        this.attendanceRepository = attendanceRepository;
        this.classSessionRepository = classSessionRepository;
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.studentAccessGuard = studentAccessGuard;
        this.qrTokenService = qrTokenService;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
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

    /**
     * Teacher taps "Show QR" for today's session. Creates (or reuses) the
     * ClassSession up front, same as markAttendance does, so selfCheckIn
     * below never has to worry about "does this session exist yet."
     */
    @Transactional
    public com.teacheros.modules.attendance.dto.AttendanceDtos.QrTokenResponse generateQrToken(UUID batchId,
            java.time.LocalDate sessionDate) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found"));

        classSessionRepository.findByTenantIdAndBatchIdAndSessionDate(tenantId, batchId, sessionDate)
                .orElseGet(() -> classSessionRepository.save(new ClassSession(tenantId, batchId, sessionDate)));

        String token = qrTokenService.issue(tenantId, batchId, sessionDate);
        return new com.teacheros.modules.attendance.dto.AttendanceDtos.QrTokenResponse(
                token, qrTokenService.expiryFor(java.time.Instant.now()));
    }

    /**
     * Student scans the QR and this marks THEIR OWN attendance PRESENT for
     * that session - no teacher/staff round-trip. Every check is a hard
     * failure, not a soft "ignore and continue", because this is the one
     * attendance path a student controls themselves:
     * - token invalid/expired -> ask them to re-scan
     * - token's tenant doesn't match the caller's tenant -> reject
     * - caller has no linked student record, or it's in a different batch
     * than the token -> reject (stops a student checking into someone
     * else's class)
     */
    @Transactional
    public StudentAttendanceRecord selfCheckIn(String token) {
        AttendanceQrTokenService.QrPayload payload = qrTokenService.verify(token)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "QR_EXPIRED_OR_INVALID",
                        "This QR code has expired. Ask your teacher to show a fresh one."));

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        if (!payload.tenantId().equals(tenantId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QR_EXPIRED_OR_INVALID", "This QR code is not valid here.");
        }

        UUID userId = currentTenantProvider.getCurrentUserId();
        String phoneNumber = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found - this should never happen"))
                .getPhoneNumber();

        var student = studentRepository.findFirstByTenantIdAndPhoneNumberAndActiveTrue(tenantId, phoneNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_STUDENT_RECORD",
                        "No student record is linked to this account."));

        if (!payload.batchId().equals(student.getBatchId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WRONG_BATCH",
                    "This QR code is for a different batch than yours.");
        }

        ClassSession session = classSessionRepository
                .findByTenantIdAndBatchIdAndSessionDate(tenantId, payload.batchId(), payload.sessionDate())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                        "This session no longer exists."));

        Attendance attendance = attendanceRepository
                .findByTenantIdAndClassSessionIdAndStudentId(tenantId, session.getId(), student.getId())
                .orElseGet(() -> new Attendance(tenantId, session.getId(), student.getId(),
                        AttendanceStatus.PRESENT, userId));
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setMarkedByUserId(userId);
        attendance.setMarkedAt(java.time.Instant.now());
        attendance = attendanceRepository.save(attendance);

        return new StudentAttendanceRecord(attendance.getStudentId(), attendance.getStatus());
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
