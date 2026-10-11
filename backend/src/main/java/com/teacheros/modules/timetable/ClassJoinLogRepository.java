package com.teacheros.modules.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassJoinLogRepository extends JpaRepository<ClassJoinLog, UUID> {

    Optional<ClassJoinLog> findByTenantIdAndTimetableSlotIdAndStudentIdAndSessionDate(
            UUID tenantId, UUID timetableSlotId, UUID studentId, LocalDate sessionDate);

    List<ClassJoinLog> findByTenantIdAndTimetableSlotIdAndSessionDateOrderByJoinedAtAsc(
            UUID tenantId, UUID timetableSlotId, LocalDate sessionDate);
}
