package com.teacheros.modules.notification;

import com.teacheros.modules.leave.LeaveDecidedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LeaveDecisionListener {

    private static final Logger log = LoggerFactory.getLogger(LeaveDecisionListener.class);

    private final NotificationService notificationService;

    public LeaveDecisionListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // AFTER_COMMIT: never tell a guardian about a decision that rolled back.
    // Failures are swallowed - a bad SMS must never undo or fail the decision.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeaveDecided(LeaveDecidedEvent event) {
        try {
            notificationService.notifyLeaveDecision(event.tenantId(), event.studentId(), event.approved(),
                    event.fromDate(), event.toDate(), event.note());
        } catch (Exception e) {
            log.error("Leave decision notification failed for tenant {}: {}", event.tenantId(), e.getMessage(), e);
        }
    }
}
