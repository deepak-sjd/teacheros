package com.teacheros.modules.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND (:status IS NULL OR l.status = :status)
            AND (:assignedTo IS NULL OR l.assignedTo = :assignedTo)
            """)
    Page<Lead> search(@Param("tenantId") UUID tenantId, @Param("status") LeadStatus status,
            @Param("assignedTo") UUID assignedTo, Pageable pageable);

    @Query("""
            SELECT l.status AS status, COUNT(l) AS total FROM Lead l
            WHERE l.tenantId = :tenantId GROUP BY l.status
            """)
    List<StatusCount> countByStatus(@Param("tenantId") UUID tenantId);

    // Public-intake dedupe: same phone already waiting as NEW recently.
    boolean existsByTenantIdAndPhoneAndStatusAndCreatedAtAfter(
            UUID tenantId, String phone, LeadStatus status, Instant after);

    interface StatusCount {
        LeadStatus getStatus();

        long getTotal();
    }
}
