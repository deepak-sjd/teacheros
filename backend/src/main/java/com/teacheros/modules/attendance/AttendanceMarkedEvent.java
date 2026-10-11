package com.teacheros.modules.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published by AttendanceService after attendance is saved; listeners run only
 * after commit.
 */
public record AttendanceMarkedEvent(UUID tenantId, LocalDate sessionDate, List<UUID> absentStudentIds) {
}
