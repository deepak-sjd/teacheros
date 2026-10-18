package com.teacheros.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {

    Optional<ClassSession> findByTenantIdAndBatchIdAndSessionDate(UUID tenantId, UUID batchId, LocalDate sessionDate);

    long countByTenantIdAndBatchIdAndCancelledFalse(UUID tenantId, UUID batchId);

    List<ClassSession> findByTenantIdAndBatchIdOrderBySessionDateDesc(UUID tenantId, UUID batchId);

    // Used by LeaveRequestService to excuse the sessions a leave covers.
    List<ClassSession> findByTenantIdAndBatchIdAndSessionDateBetweenAndCancelledFalse(
            UUID tenantId, UUID batchId, LocalDate from, LocalDate to);
}
