package com.teacheros.modules.leave;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.attendance.Attendance;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.attendance.AttendanceStatus;
import com.teacheros.modules.attendance.ClassSession;
import com.teacheros.modules.attendance.ClassSessionRepository;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.DecideLeaveRequest;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.LeaveRequestResponse;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.SubmitLeaveRequest;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leave requests: a PARENT (or the STUDENT) asks for leave for their own
 * linked student, staff approve or reject.
 *
 * Access: submit and history go through StudentAccessGuard (a parent/student
 * only ever reaches their own child's/own requests - 404 otherwise). Staff
 * deciding and the pending list are gated at the controller by the
 * ATTENDANCE_MARK permission.
 *
 * Attendance rule (same EXCUSED rule as AttendanceRepository): approving a
 * request marks the covered sessions EXCUSED, which leaves them out of the
 * attendance-percentage denominator. A session the teacher already marked
 * PRESENT (or LATE, which also earns attendance credit) is never
 * overwritten. Sessions that don't exist yet are handled when they are
 * created - see AttendanceService#markAttendance.
 *
 * The leave reason may contain health details: it is returned only to
 * staff and the student's own family, and is deliberately left out of the
 * audit metadata.
 */
@Service
public class LeaveRequestService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    // A parent may report yesterday's illness, so a short back-dated window
    // is allowed - approval then retro-marks an already-recorded ABSENT as
    // EXCUSED. Anything older than this has to go through the teacher.
    static final int MAX_BACKDATE_DAYS = 7;
    // Inclusive length of one request.
    static final int MAX_RANGE_DAYS = 60;

    private final LeaveRequestRepository leaveRequestRepository;
    private final StudentRepository studentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final BatchRepository batchRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public LeaveRequestService(
            LeaveRequestRepository leaveRequestRepository,
            StudentRepository studentRepository,
            ClassSessionRepository classSessionRepository,
            AttendanceRepository attendanceRepository,
            BatchRepository batchRepository,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.studentRepository = studentRepository;
        this.classSessionRepository = classSessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.batchRepository = batchRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    // ---- Family: submit and history -------------------------------------

    @Transactional
    public LeaveRequestResponse submit(UUID studentId, SubmitLeaveRequest request) {
        studentAccessGuard.assertCanView(studentId);

        // Staff pass the guard (it's a no-op for them) but leave is requested
        // by the family; staff record absences through attendance instead.
        String role = currentTenantProvider.getCurrentRole();
        if (!"PARENT".equals(role) && !"STUDENT".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ONLY_FAMILY_CAN_REQUEST",
                    "Only a parent or the student can request leave.");
        }

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .filter(Student::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        validateRange(request.fromDate(), request.toDate());

        boolean overlaps = leaveRequestRepository
                .existsByTenantIdAndStudentIdAndStatusInAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        tenantId, studentId, EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                        request.toDate(), request.fromDate());
        if (overlaps) {
            throw new ApiException(HttpStatus.CONFLICT, "LEAVE_OVERLAP",
                    "There is already a pending or approved leave request that overlaps these dates.");
        }

        LeaveRequest saved = leaveRequestRepository.save(new LeaveRequest(tenantId, studentId,
                request.fromDate(), request.toDate(), request.reason().trim(), userId));
        return toResponse(saved, student.getFullName());
    }

    public List<LeaveRequestResponse> listForStudent(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        return leaveRequestRepository
                .findByTenantIdAndStudentIdOrderByFromDateDescCreatedAtDesc(tenantId, studentId).stream()
                .map(l -> toResponse(l, student.getFullName()))
                .toList();
    }

    // ---- Staff: pending list and decision --------------------------------

    public Page<LeaveRequestResponse> listPending(UUID batchId, Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        if (batchId != null) {
            batchRepository.findByIdAndTenantId(batchId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        }

        Page<LeaveRequest> page = leaveRequestRepository.findPending(tenantId, batchId, pageable);

        // Ids come from tenant-scoped leave rows, so this lookup can't cross tenants.
        Map<UUID, String> names = studentRepository
                .findAllById(page.getContent().stream().map(LeaveRequest::getStudentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName, (a, b) -> a));

        return page.map(l -> toResponse(l, names.getOrDefault(l.getStudentId(), "Student")));
    }

    @Transactional
    public LeaveRequestResponse decide(UUID leaveRequestId, DecideLeaveRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        LeaveRequest leave = leaveRequestRepository.findForDecision(leaveRequestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", leaveRequestId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED",
                    "This request has already been " + leave.getStatus().name().toLowerCase() + ".");
        }

        boolean approve = "APPROVED".equals(request.decision());
        String note = blankToNull(request.note());
        if (!approve && note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE_REQUIRED",
                    "Add a short note explaining why the request is being rejected.");
        }

        Student student = studentRepository.findByIdAndTenantId(leave.getStudentId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", leave.getStudentId()));

        leave.setStatus(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
        leave.setDecidedByUserId(userId);
        leave.setDecidedAt(Instant.now());
        leave.setDecisionNote(note);
        // Not reassigned: `leave` is captured by a lambda above, so it must stay
        // effectively final. It's a managed entity, so save() returns the same
        // instance.
        leaveRequestRepository.save(leave);

        int sessionsExcused = approve ? applyExcused(tenantId, userId, student, leave) : 0;

        // No reason text here on purpose - see class javadoc.
        auditService.record(AuditActions.LEAVE_DECIDED, AuditActions.LEAVE_REQUEST, leave.getId(),
                AuditService.meta(
                        "studentId", leave.getStudentId(),
                        "decision", leave.getStatus(),
                        "fromDate", leave.getFromDate(),
                        "toDate", leave.getToDate(),
                        "sessionsExcused", sessionsExcused));

        eventPublisher.publishEvent(new LeaveDecidedEvent(tenantId, leave.getStudentId(), approve,
                leave.getFromDate(), leave.getToDate(), note));

        return toResponse(leave, student.getFullName());
    }

    // ---- helpers --------------------------------------------------------

    /**
     * Marks every existing, non-cancelled session of the student's batch
     * inside the range EXCUSED. Returns how many records changed.
     * PRESENT and LATE are never overwritten; an existing ABSENT becomes
     * EXCUSED (that's the point of a retro-active approval); a session where
     * the student has no record yet gets an EXCUSED one.
     */
    private int applyExcused(UUID tenantId, UUID decidedBy, Student student, LeaveRequest leave) {
        if (student.getBatchId() == null) {
            return 0; // no batch -> no sessions to excuse
        }

        List<ClassSession> sessions = classSessionRepository
                .findByTenantIdAndBatchIdAndSessionDateBetweenAndCancelledFalse(
                        tenantId, student.getBatchId(), leave.getFromDate(), leave.getToDate());

        int changed = 0;
        for (ClassSession session : sessions) {
            Optional<Attendance> existing = attendanceRepository
                    .findByTenantIdAndClassSessionIdAndStudentId(tenantId, session.getId(), student.getId());

            if (existing.isPresent()) {
                Attendance a = existing.get();
                AttendanceStatus current = a.getStatus();
                if (current != AttendanceStatus.ABSENT) {
                    continue; // PRESENT / LATE are kept, EXCUSED is already done
                }
                a.setStatus(AttendanceStatus.EXCUSED);
                a.setMarkedByUserId(decidedBy);
                a.setMarkedAt(Instant.now());
                attendanceRepository.save(a);
            } else {
                attendanceRepository.save(new Attendance(tenantId, session.getId(), student.getId(),
                        AttendanceStatus.EXCUSED, decidedBy));
            }
            changed++;
        }
        return changed;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE",
                    "The end date can't be before the start date.");
        }
        LocalDate today = LocalDate.now(APP_ZONE);
        if (from.isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_TOO_OLD",
                    "Leave can only be requested for the last " + MAX_BACKDATE_DAYS
                            + " days or later. Please speak to the teacher for older dates.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEAVE_TOO_LONG",
                    "A single request can cover at most " + MAX_RANGE_DAYS + " days.");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private LeaveRequestResponse toResponse(LeaveRequest l, String studentName) {
        return new LeaveRequestResponse(l.getId(), l.getStudentId(), studentName, l.getFromDate(), l.getToDate(),
                l.getReason(), l.getStatus(), l.getCreatedAt(), l.getDecidedAt(), l.getDecisionNote());
    }
}
