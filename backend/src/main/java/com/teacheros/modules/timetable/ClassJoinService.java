package com.teacheros.modules.timetable;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.timetable.dto.ClassJoinDtos.JoinResponse;
import com.teacheros.modules.timetable.dto.ClassJoinDtos.JoinedStudent;
import com.teacheros.modules.timetable.dto.ClassJoinDtos.SessionJoinsResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Records and lists "student clicked Join" events for timetable slots. A
 * log only - nothing here marks attendance.
 *
 * recordJoin() is deliberately NOT @Transactional: two near-simultaneous
 * clicks can race on the unique constraint, and catching the violation
 * inside a transaction would leave it rollback-only. Without a
 * surrounding transaction, repository.save() commits on its own, so the
 * loser of the race can catch the exception and just read the winner's row.
 */
@Service
public class ClassJoinService {

    // Same zone the rest of the app assumes (see DoubtService, AnalyticsService).
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private final ClassJoinLogRepository joinLogRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final StudentRepository studentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public ClassJoinService(
            ClassJoinLogRepository joinLogRepository,
            TimetableSlotRepository timetableSlotRepository,
            StudentRepository studentRepository,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.joinLogRepository = joinLogRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.studentRepository = studentRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    /**
     * STUDENT only (enforced at the controller). The student comes from the
     * JWT via StudentAccessGuard#resolveCurrentStudentId, never from the
     * request. A slot belonging to another batch is a 404, same as every
     * other batch-scoped read.
     */
    public JoinResponse recordJoin(UUID slotId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();

        TimetableSlot slot = requireSlot(tenantId, slotId);
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        if (!slot.getBatchId().equals(student.getBatchId())) {
            throw new ResourceNotFoundException("TimetableSlot", slotId);
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        if (slot.getDayOfWeek() != today.getDayOfWeek().getValue()) {
            return new JoinResponse(false, null);
        }

        var existing = joinLogRepository.findByTenantIdAndTimetableSlotIdAndStudentIdAndSessionDate(
                tenantId, slotId, studentId, today);
        if (existing.isPresent()) {
            return new JoinResponse(true, existing.get().getJoinedAt());
        }

        try {
            ClassJoinLog saved = joinLogRepository.save(new ClassJoinLog(tenantId, slotId, studentId, today));
            return new JoinResponse(true, saved.getJoinedAt());
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a simultaneous click - the row already exists.
            return joinLogRepository
                    .findByTenantIdAndTimetableSlotIdAndStudentIdAndSessionDate(tenantId, slotId, studentId, today)
                    .map(log -> new JoinResponse(true, log.getJoinedAt()))
                    .orElse(new JoinResponse(true, null));
        }
    }

    /**
     * Staff only (enforced at the controller). date defaults to today; a
     * date on which the slot isn't scheduled simply has no joins.
     */
    public SessionJoinsResponse listJoins(UUID slotId, LocalDate date) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        TimetableSlot slot = requireSlot(tenantId, slotId);
        LocalDate sessionDate = date != null ? date : LocalDate.now(APP_ZONE);

        List<Student> roster = studentRepository.findByTenantIdAndBatchIdAndActiveTrue(tenantId, slot.getBatchId());
        Map<UUID, Student> rosterById = roster.stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        List<JoinedStudent> joined = joinLogRepository
                .findByTenantIdAndTimetableSlotIdAndSessionDateOrderByJoinedAtAsc(tenantId, slotId, sessionDate)
                .stream()
                .map(log -> new JoinedStudent(log.getStudentId(), nameOf(tenantId, log.getStudentId(), rosterById),
                        log.getJoinedAt()))
                .toList();

        return new SessionJoinsResponse(slot.getId(), slot.getBatchId(), sessionDate, slot.getSubject(),
                slot.getStartTime(), slot.getEndTime(), roster.size(), joined);
    }

    private TimetableSlot requireSlot(UUID tenantId, UUID slotId) {
        return timetableSlotRepository.findByIdAndTenantId(slotId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableSlot", slotId));
    }

    // A student who joined and was later deactivated or moved batch isn't
    // in today's roster - look them up directly rather than dropping them.
    private String nameOf(UUID tenantId, UUID studentId, Map<UUID, Student> rosterById) {
        Student fromRoster = rosterById.get(studentId);
        if (fromRoster != null) {
            return fromRoster.getFullName();
        }
        return studentRepository.findByIdAndTenantId(studentId, tenantId)
                .map(Student::getFullName)
                .orElse("Unknown student");
    }
}
