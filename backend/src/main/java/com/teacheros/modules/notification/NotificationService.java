package com.teacheros.modules.notification;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.attendance.AttendancePercentageCalculator;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeRepository;
import com.teacheros.modules.fee.FeeStatus;
import com.teacheros.modules.notification.dto.NotificationDtos.GenerateResult;
import com.teacheros.modules.notification.dto.NotificationDtos.NotificationResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Alert generation. Two entry styles share the same dedupe + delivery path:
 *
 * - Staff-triggered buttons (generateLowAttendanceAlerts /
 * generateFeeReminders):
 * tenant comes from the authenticated request.
 * - Scheduled / event-driven (the ...ForTenant methods, notifyAbsences): the
 * caller passes tenantId explicitly, since a scheduler thread has no
 * SecurityContext. That tenantId always originates from our own tenants
 * table or a committed event - never from client input.
 *
 * Every generation call is deduped against COOLDOWN so repeated runs don't
 * create duplicate alerts for the same student.
 */
@Service
public class NotificationService {

    private static final Duration COOLDOWN = Duration.ofDays(3);
    // "Due today" must still fire when a "due soon" reminder went out 1-2 days
    // earlier (both are FEE_DUE), so it uses a shorter window than COOLDOWN.
    private static final Duration DUE_TODAY_COOLDOWN = Duration.ofDays(1);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final FeeRepository feeRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;
    private final SmsSender smsSender;
    private final EmailSender emailSender;

    public NotificationService(
            NotificationRepository notificationRepository,
            StudentRepository studentRepository,
            AttendanceRepository attendanceRepository,
            FeeRepository feeRepository,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider,
            SmsSender smsSender,
            EmailSender emailSender) {
        this.notificationRepository = notificationRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.feeRepository = feeRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
    }

    // Every alert is recorded in-app first (source of truth for the parent
    // portal) and then best-effort pushed out by SMS/email if the guardian
    // has that contact info on file. Delivery failures are swallowed inside
    // the senders themselves - a bad phone number must never roll back the
    // Notification row or block the rest of the batch.
    private void deliver(Student student, String title, String message) {
        if (student.getGuardianPhone() != null && !student.getGuardianPhone().isBlank()) {
            smsSender.send(student.getGuardianPhone(), message);
        }
        if (student.getGuardianEmail() != null && !student.getGuardianEmail().isBlank()) {
            emailSender.send(student.getGuardianEmail(), title, message);
        }
    }

    // ---- Low attendance -------------------------------------------------

    @Transactional
    public GenerateResult generateLowAttendanceAlerts(double thresholdPercentage) {
        return generateLowAttendanceAlertsForTenant(currentTenantProvider.getCurrentTenantId(), thresholdPercentage);
    }

    @Transactional
    public GenerateResult generateLowAttendanceAlertsForTenant(UUID tenantId, double thresholdPercentage) {
        Instant cooldownStart = Instant.now().minus(COOLDOWN);
        int created = 0;

        for (Student student : studentRepository.findAllByTenantIdAndActiveTrue(tenantId)) {
            long recorded = attendanceRepository.countRecordedSessions(tenantId, student.getId());
            if (recorded == 0)
                continue; // nothing to alert on yet

            long attended = attendanceRepository.countAttendedSessions(tenantId, student.getId());
            double percentage = AttendancePercentageCalculator.calculate(recorded, attended);
            if (percentage >= thresholdPercentage)
                continue;

            boolean alreadyAlerted = notificationRepository.existsByTenantIdAndStudentIdAndTypeAndCreatedAtAfter(
                    tenantId, student.getId(), NotificationType.LOW_ATTENDANCE, cooldownStart);
            if (alreadyAlerted)
                continue;

            String message = "%s's attendance is %.0f%%, below the %.0f%% threshold."
                    .formatted(student.getFullName(), percentage, thresholdPercentage);
            notificationRepository.save(new Notification(tenantId, student.getId(),
                    NotificationType.LOW_ATTENDANCE, "Attendance alert", message));
            deliver(student, "Attendance alert", message);
            created++;
        }

        return new GenerateResult(created);
    }

    // ---- Fee reminders --------------------------------------------------

    @Transactional
    public GenerateResult generateFeeReminders() {
        return generateFeeRemindersForTenant(currentTenantProvider.getCurrentTenantId());
    }

    /**
     * Manual-button behaviour: every outstanding fee, typed by its stored status.
     */
    @Transactional
    public GenerateResult generateFeeRemindersForTenant(UUID tenantId) {
        Instant cooldownStart = Instant.now().minus(COOLDOWN);
        int created = 0;

        List<Fee> outstanding = feeRepository.findAllByTenantIdAndStatusIn(
                tenantId, List.of(FeeStatus.PENDING, FeeStatus.PARTIALLY_PAID, FeeStatus.OVERDUE));

        for (Fee fee : outstanding) {
            NotificationType type = fee.getStatus() == FeeStatus.OVERDUE
                    ? NotificationType.FEE_OVERDUE
                    : NotificationType.FEE_DUE;

            boolean alreadyAlerted = notificationRepository.existsByTenantIdAndStudentIdAndTypeAndCreatedAtAfter(
                    tenantId, fee.getStudentId(), type, cooldownStart);
            if (alreadyAlerted)
                continue;

            String title = type == NotificationType.FEE_OVERDUE ? "Fee overdue" : "Fee due soon";
            String message = "A fee of ₹%s is due on %s.".formatted(fee.getAmountDue(), fee.getDueDate());
            notificationRepository.save(new Notification(tenantId, fee.getStudentId(), type, title, message));
            studentRepository.findByIdAndTenantId(fee.getStudentId(), tenantId)
                    .ifPresent(student -> deliver(student, title, message));
            created++;
        }

        return new GenerateResult(created);
    }

    /**
     * Scheduled behaviour: only fees due within [today, today + daysBefore]
     * ("before due" / "on due") or already past due ("overdue"). Overdue is
     * derived from dueDate here because FeeStatus.OVERDUE is never persisted.
     */
    @Transactional
    public GenerateResult generateScheduledFeeRemindersForTenant(UUID tenantId, LocalDate today, int daysBefore) {
        Instant now = Instant.now();
        LocalDate windowEnd = today.plusDays(Math.max(daysBefore, 0));
        int created = 0;

        List<Fee> outstanding = feeRepository.findAllByTenantIdAndStatusIn(
                tenantId, List.of(FeeStatus.PENDING, FeeStatus.PARTIALLY_PAID, FeeStatus.OVERDUE));

        for (Fee fee : outstanding) {
            if (fee.getDueDate().isAfter(windowEnd))
                continue; // not due soon enough to remind yet

            boolean dueToday = fee.getDueDate().isEqual(today);
            boolean overdue = fee.getStatus() == FeeStatus.OVERDUE || fee.getDueDate().isBefore(today);
            NotificationType type = overdue ? NotificationType.FEE_OVERDUE : NotificationType.FEE_DUE;
            Duration cooldown = dueToday ? DUE_TODAY_COOLDOWN : COOLDOWN;

            boolean alreadyAlerted = notificationRepository.existsByTenantIdAndStudentIdAndTypeAndCreatedAtAfter(
                    tenantId, fee.getStudentId(), type, now.minus(cooldown));
            if (alreadyAlerted)
                continue;

            String title = overdue ? "Fee overdue" : dueToday ? "Fee due today" : "Fee due soon";
            String message = overdue
                    ? "A fee of ₹%s was due on %s and is still unpaid.".formatted(fee.getAmountDue(), fee.getDueDate())
                    : "A fee of ₹%s is due on %s.".formatted(fee.getAmountDue(), fee.getDueDate());
            notificationRepository.save(new Notification(tenantId, fee.getStudentId(), type, title, message));
            studentRepository.findByIdAndTenantId(fee.getStudentId(), tenantId)
                    .ifPresent(student -> deliver(student, title, message));
            created++;
        }

        return new GenerateResult(created);
    }

    // ---- Same-day absent alert ------------------------------------------

    /**
     * Alerts the guardian of each absent student, once per student per day,
     * and only when the attendance being marked is for today (IST) - a
     * teacher back-filling or correcting an old session shouldn't text
     * parents about last week.
     *
     * REQUIRES_NEW is essential: this runs from an AFTER_COMMIT listener,
     * where joining the already-committed original transaction would
     * silently never persist the Notification rows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyAbsences(UUID tenantId, LocalDate sessionDate, Collection<UUID> absentStudentIds) {
        if (!sessionDate.equals(LocalDate.now(APP_ZONE))) {
            return 0;
        }
        Instant startOfDay = sessionDate.atStartOfDay(APP_ZONE).toInstant();
        int created = 0;

        for (UUID studentId : absentStudentIds) {
            // Tenant-scoped lookup also drops any foreign/unknown student IDs.
            Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                    .filter(Student::isActive)
                    .orElse(null);
            if (student == null)
                continue;

            boolean alreadyAlerted = notificationRepository.existsByTenantIdAndStudentIdAndTypeAndCreatedAtAfter(
                    tenantId, studentId, NotificationType.ABSENT, startOfDay);
            if (alreadyAlerted)
                continue;

            String message = "%s was marked absent in today's class.".formatted(student.getFullName());
            notificationRepository.save(new Notification(tenantId, studentId,
                    NotificationType.ABSENT, "Absent today", message));
            deliver(student, "Absent today", message);
            created++;
        }
        return created;
    }

    // ---- Reads ----------------------------------------------------------

    // Open to PARENT/STUDENT via the controller (no @PreAuthorize there) -
    // studentAccessGuard restricts it to their own linked student, same
    // pattern as attendance summary / fee-for-student.
    public List<NotificationResponse> listForStudent(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return notificationRepository.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void markRead(UUID notificationId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Notification notification = notificationRepository.findByIdAndTenantId(notificationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        studentAccessGuard.assertCanView(notification.getStudentId());
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getStudentId(), n.getType(), n.getTitle(),
                n.getMessage(), n.getReadAt() != null, n.getCreatedAt());
    }
}
