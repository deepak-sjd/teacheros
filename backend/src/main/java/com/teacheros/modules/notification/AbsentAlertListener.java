package com.teacheros.modules.notification;

import com.teacheros.modules.attendance.AttendanceMarkedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AbsentAlertListener {

    private static final Logger log = LoggerFactory.getLogger(AbsentAlertListener.class);

    private final NotificationService notificationService;

    public AbsentAlertListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // AFTER_COMMIT: never alert a guardian for attendance that rolled back.
    // Failures are swallowed here - a bad SMS must never affect marking.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttendanceMarked(AttendanceMarkedEvent event) {
        if (event.absentStudentIds().isEmpty()) {
            return;
        }
        try {
            notificationService.notifyAbsences(event.tenantId(), event.sessionDate(), event.absentStudentIds());
        } catch (Exception e) {
            log.error("Absent alerts failed for tenant {}: {}", event.tenantId(), e.getMessage(), e);
        }
    }
}
