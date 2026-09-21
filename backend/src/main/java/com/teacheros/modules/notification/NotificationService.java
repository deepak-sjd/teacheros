package com.teacheros.modules.notification;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Staff-triggered alert generation, not a background scheduler - matches
 * Section 26 of the product spec ("don't spam parents" + "configurable
 * frequency"): a teacher taps "Send reminders" when they actually want to,
 * rather than the system silently deciding on its own. Keeps this phase
 * free of cron/Quartz infrastructure entirely.
 *
 * Every generation call is deduped against COOLDOWN so mashing the button
 * twice in one day doesn't create duplicate alerts for the same student.
 */
@Service
public class NotificationService {

    private static final java.time.Duration COOLDOWN = java.time.Duration.ofDays(3);

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final FeeRepository feeRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public NotificationService(
            NotificationRepository notificationRepository,
            StudentRepository studentRepository,
            AttendanceRepository attendanceRepository,
            FeeRepository feeRepository,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.notificationRepository = notificationRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.feeRepository = feeRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public GenerateResult generateLowAttendanceAlerts(double thresholdPercentage) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
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
            created++;
        }

        return new GenerateResult(created);
    }

    @Transactional
    public GenerateResult generateFeeReminders() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
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
            created++;
        }

        return new GenerateResult(created);
    }

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
                .orElseThrow(() -> new com.teacheros.common.exception.ResourceNotFoundException(
                        "Notification", notificationId));
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
