package com.teacheros.modules.fee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeRepository extends JpaRepository<Fee, UUID> {

    Optional<Fee> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Fee> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);

    Page<Fee> findByTenantIdAndStatusIn(UUID tenantId, List<FeeStatus> statuses, Pageable pageable);

    /**
     * Unpaged variant used by NotificationService#generateFeeReminders - it
     * has to walk every outstanding fee in the tenant in one pass to check
     * each against the dedupe cooldown, so pagination doesn't apply there.
     */
    List<Fee> findAllByTenantIdAndStatusIn(UUID tenantId, List<FeeStatus> statuses);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Fee f SET f.status = :overdue
            WHERE f.tenantId = :tenantId AND f.dueDate < :today AND f.status IN :open
            """)
    int markOverdueForTenant(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today,
            @Param("overdue") FeeStatus overdue, @Param("open") List<FeeStatus> open);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Fee f SET f.status = :overdue
            WHERE f.dueDate < :today AND f.status IN :open
            """)
    int markOverdueAll(@Param("today") LocalDate today,
            @Param("overdue") FeeStatus overdue, @Param("open") List<FeeStatus> open);

    /**
     * Tenant-scoped sweep: PENDING / PARTIALLY_PAID fees past their due date become
     * OVERDUE.
     */
    @Transactional
    default int sweepOverdueForTenant(UUID tenantId, LocalDate today) {
        return markOverdueForTenant(tenantId, today, FeeStatus.OVERDUE,
                List.of(FeeStatus.PENDING, FeeStatus.PARTIALLY_PAID));
    }

    /** All-tenant sweep, used only by the scheduled job. */
    @Transactional
    default int sweepOverdueAll(LocalDate today) {
        return markOverdueAll(today, FeeStatus.OVERDUE, List.of(FeeStatus.PENDING, FeeStatus.PARTIALLY_PAID));
    }
}
