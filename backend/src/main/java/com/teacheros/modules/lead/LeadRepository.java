package com.teacheros.modules.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.time.LocalDate;

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

    Optional<Lead> findFirstByTenantIdAndPhoneOrderByCreatedAtDesc(UUID tenantId, String phone);

    // Staff page: follow-ups due today or overdue, optionally only the caller's.
    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId AND l.nextFollowUp IS NOT NULL AND l.nextFollowUp <= :today
            AND l.status IN :statuses
            AND (:assignedTo IS NULL OR l.assignedTo = :assignedTo)
            ORDER BY l.nextFollowUp ASC
            """)
    List<Lead> findDueFollowUps(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today,
            @Param("statuses") Collection<LeadStatus> statuses, @Param("assignedTo") UUID assignedTo);

    // Reminder job only (system job, so deliberately not tenant-scoped; every lead
    // carries its own tenant).
    List<Lead> findByNextFollowUpLessThanEqualAndAssignedToIsNotNullAndStatusIn(
            LocalDate today, Collection<LeadStatus> statuses);

    // Atomic claim: returns 1 only for the caller that marks this follow-up date as
    // reminded.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE leads SET follow_up_reminded_for = next_follow_up
            WHERE id = :id AND next_follow_up IS NOT NULL
            AND follow_up_reminded_for IS DISTINCT FROM next_follow_up
            """, nativeQuery = true)
    int claimFollowUpReminder(@Param("id") UUID id);

    @Query("""
            SELECT l.status AS status, COUNT(l) AS total FROM Lead l
            WHERE l.tenantId = :tenantId AND l.createdAt >= :from AND l.createdAt < :to
            GROUP BY l.status
            """)
    List<StatusCount> countByStatusBetween(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT l.source AS source, l.status AS status, COUNT(l) AS total FROM Lead l
            WHERE l.tenantId = :tenantId AND l.createdAt >= :from AND l.createdAt < :to
            GROUP BY l.source, l.status
            """)
    List<SourceStatusCount> countBySourceAndStatusBetween(@Param("tenantId") UUID tenantId,
            @Param("from") Instant from, @Param("to") Instant to);

    interface SourceStatusCount {
        LeadSource getSource();

        LeadStatus getStatus();

        long getTotal();
    }
}
