package com.teacheros.modules.leave;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    Optional<LeaveRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Row-locked variant for deciding a request, so two staff members
     * approving/rejecting the same request at once can't both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LeaveRequest l WHERE l.id = :id AND l.tenantId = :tenantId")
    Optional<LeaveRequest> findForDecision(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<LeaveRequest> findByTenantIdAndStudentIdOrderByFromDateDescCreatedAtDesc(UUID tenantId, UUID studentId);

    /**
     * Overlap check on submit: an existing request [from, to] overlaps a new
     * one [newFrom, newTo] exactly when existing.from <= newTo AND
     * existing.to >= newFrom. Only PENDING/APPROVED count - a rejected
     * request must not block asking again.
     */
    boolean existsByTenantIdAndStudentIdAndStatusInAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            UUID tenantId, UUID studentId, Collection<LeaveStatus> statuses, LocalDate newTo, LocalDate newFrom);

    /**
     * Staff "pending" list, optionally narrowed to one batch (null = every
     * batch), oldest leave first.
     */
    @Query(value = """
            SELECT l FROM LeaveRequest l
            JOIN Student s ON s.id = l.studentId
            WHERE l.tenantId = :tenantId AND l.status = com.teacheros.modules.leave.LeaveStatus.PENDING
            AND (:batchId IS NULL OR s.batchId = :batchId)
            ORDER BY l.fromDate ASC, l.createdAt ASC
            """, countQuery = """
            SELECT COUNT(l) FROM LeaveRequest l
            JOIN Student s ON s.id = l.studentId
            WHERE l.tenantId = :tenantId AND l.status = com.teacheros.modules.leave.LeaveStatus.PENDING
            AND (:batchId IS NULL OR s.batchId = :batchId)
            """)
    Page<LeaveRequest> findPending(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            Pageable pageable);

    /**
     * Which of these students are on APPROVED leave on this date. Used by
     * AttendanceService#markAttendance to turn a teacher's ABSENT into
     * EXCUSED for a session that didn't exist yet when the leave was approved.
     */
    @Query("""
            SELECT DISTINCT l.studentId FROM LeaveRequest l
            WHERE l.tenantId = :tenantId
            AND l.status = com.teacheros.modules.leave.LeaveStatus.APPROVED
            AND l.studentId IN :studentIds
            AND l.fromDate <= :date AND l.toDate >= :date
            """)
    List<UUID> findApprovedStudentIdsCovering(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date,
            @Param("studentIds") Collection<UUID> studentIds);
}
