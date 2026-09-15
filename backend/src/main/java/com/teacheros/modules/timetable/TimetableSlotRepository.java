package com.teacheros.modules.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, UUID> {

    Optional<TimetableSlot> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TimetableSlot> findByTenantIdAndBatchIdOrderByDayOfWeekAscStartTimeAsc(UUID tenantId, UUID batchId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
