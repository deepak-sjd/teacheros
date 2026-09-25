package com.teacheros.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    List<Attendance> findByTenantIdAndClassSessionId(UUID tenantId, UUID classSessionId);

    Optional<Attendance> findByTenantIdAndClassSessionIdAndStudentId(
            UUID tenantId, UUID classSessionId, UUID studentId);

    List<Attendance> findByTenantIdAndStudentIdOrderByMarkedAtDesc(UUID tenantId, UUID studentId);

    /**
     * Total sessions this student has an attendance record for (i.e. sessions
     * that were actually held, not cancelled) - the denominator for the
     * percentage calculation.
     */
    @Query("""
            SELECT COUNT(a) FROM Attendance a
            JOIN ClassSession cs ON cs.id = a.classSessionId
            WHERE a.tenantId = :tenantId AND a.studentId = :studentId AND cs.cancelled = false
            """)
    long countRecordedSessions(@Param("tenantId") UUID tenantId, @Param("studentId") UUID studentId);

    /**
     * Sessions where the student was marked present or late (late still
     * counts toward attendance credit; it's a separate signal from absence,
     * not treated as a full miss) - the numerator.
     */
    @Query("""
            SELECT COUNT(a) FROM Attendance a
            JOIN ClassSession cs ON cs.id = a.classSessionId
            WHERE a.tenantId = :tenantId AND a.studentId = :studentId AND cs.cancelled = false
            AND a.status IN (com.teacheros.modules.attendance.AttendanceStatus.PRESENT,
                              com.teacheros.modules.attendance.AttendanceStatus.LATE)
            """)
    long countAttendedSessions(@Param("tenantId") UUID tenantId, @Param("studentId") UUID studentId);

    /**
     * Total sessions held for a batch within a date range - the denominator
     * for ReportService's per-batch attendance percentage. Same "not
     * cancelled" rule as countRecordedSessions above, just scoped to a
     * batch + range instead of a single student's whole history.
     */
    @Query("""
            SELECT COUNT(a) FROM Attendance a
            JOIN ClassSession cs ON cs.id = a.classSessionId
            WHERE a.tenantId = :tenantId AND cs.batchId = :batchId AND cs.cancelled = false
            AND cs.sessionDate BETWEEN :from AND :to
            """)
    long countRecordedInRange(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COUNT(a) FROM Attendance a
            JOIN ClassSession cs ON cs.id = a.classSessionId
            WHERE a.tenantId = :tenantId AND cs.batchId = :batchId AND cs.cancelled = false
            AND cs.sessionDate BETWEEN :from AND :to
            AND a.status IN (com.teacheros.modules.attendance.AttendanceStatus.PRESENT,
                              com.teacheros.modules.attendance.AttendanceStatus.LATE)
            """)
    long countAttendedInRange(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
