package com.teacheros.modules.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemoBookingRepository extends JpaRepository<DemoBooking, UUID> {

    Optional<DemoBooking> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DemoBooking> findByTenantIdAndSlotIdAndLeadId(UUID tenantId, UUID slotId, UUID leadId);

    List<DemoBooking> findByTenantIdAndSlotIdOrderByCreatedAtAsc(UUID tenantId, UUID slotId);

    List<DemoBooking> findByTenantIdAndLeadIdOrderByCreatedAtDesc(UUID tenantId, UUID leadId);

    // Seats taken = every booking that isn't cancelled (attended and no-shows still
    // held a seat).
    long countByTenantIdAndSlotIdAndStatusNot(UUID tenantId, UUID slotId, DemoBookingStatus status);

    boolean existsByTenantIdAndLeadIdAndStatusNot(UUID tenantId, UUID leadId, DemoBookingStatus status);

    @Query("""
            SELECT b.slotId AS slotId, COUNT(b) AS total FROM DemoBooking b
            WHERE b.tenantId = :tenantId AND b.slotId IN :slotIds
            AND b.status <> com.teacheros.modules.demo.DemoBookingStatus.CANCELLED
            GROUP BY b.slotId
            """)
    List<SlotCount> countActiveBySlot(@Param("tenantId") UUID tenantId, @Param("slotIds") Collection<UUID> slotIds);

    // Funnel: distinct leads (created in the window) that have a booking in one of
    // the given statuses.
    @Query("""
            SELECT COUNT(DISTINCT b.leadId) FROM DemoBooking b
            WHERE b.tenantId = :tenantId AND b.status IN :statuses
            AND b.leadId IN (SELECT l.id FROM Lead l
                             WHERE l.tenantId = :tenantId AND l.createdAt >= :from AND l.createdAt < :to)
            """)
    long countDistinctLeads(@Param("tenantId") UUID tenantId,
            @Param("statuses") Collection<DemoBookingStatus> statuses,
            @Param("from") Instant from, @Param("to") Instant to);

    interface SlotCount {
        UUID getSlotId();

        long getTotal();
    }
}
