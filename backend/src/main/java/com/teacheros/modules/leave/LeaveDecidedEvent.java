package com.teacheros.modules.leave;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published by LeaveRequestService when a request is approved or rejected;
 * the notification listener runs only after the decision commits (same
 * pattern as AttendanceMarkedEvent), so a guardian is never told about a
 * decision that rolled back.
 */
public record LeaveDecidedEvent(UUID tenantId, UUID studentId, boolean approved, LocalDate fromDate,
        LocalDate toDate, String note) {
}
