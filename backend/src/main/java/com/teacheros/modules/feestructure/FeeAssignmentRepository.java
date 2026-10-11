package com.teacheros.modules.feestructure;

import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeLineType;
import com.teacheros.modules.fee.FeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev 12's own queries over the Fee entity. A second repository on the same
 * entity (rather than editing the shared FeeRepository) keeps this module's
 * PR self-contained.
 */
public interface FeeAssignmentRepository extends JpaRepository<Fee, UUID> {

    Optional<Fee> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Fee> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    List<Fee> findByTenantIdAndStudentIdOrderByDueDateAsc(UUID tenantId, UUID studentId);

    List<Fee> findByTenantIdAndStatusIn(UUID tenantId, Collection<FeeStatus> statuses);

    List<Fee> findByTenantIdAndStructureIdAndLineType(UUID tenantId, UUID structureId, FeeLineType lineType);

    /**
     * Unpaid instalments past the grace cut-off that have no late-fee row yet.
     * The partial unique index uq_fees_late_fee_parent is the hard backstop
     * against double-charging if two runs race.
     */
    @Query("""
            SELECT f FROM Fee f
            WHERE f.tenantId = :tenantId AND f.lineType = :instalment AND f.status <> :paid
              AND f.dueDate < :cutoff
              AND f.id NOT IN (
                  SELECT l.parentFeeId FROM Fee l
                  WHERE l.tenantId = :tenantId AND l.lineType = :late AND l.parentFeeId IS NOT NULL)
            """)
    List<Fee> findLateFeeCandidates(@Param("tenantId") UUID tenantId,
            @Param("instalment") FeeLineType instalment, @Param("late") FeeLineType late,
            @Param("paid") FeeStatus paid, @Param("cutoff") LocalDate cutoff);
}
