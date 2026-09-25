package com.teacheros.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarkRepository extends JpaRepository<Mark, UUID> {

    List<Mark> findByTenantIdAndTestId(UUID tenantId, UUID testId);

    Optional<Mark> findByTenantIdAndTestIdAndStudentId(UUID tenantId, UUID testId, UUID studentId);

    /**
     * A student's marks across every test they've been graded on - the
     * data behind the "report card" view. Ordered most-recent-first so a
     * student/parent sees their latest results at the top.
     */
    @Query("""
            SELECT m FROM Mark m
            JOIN Assessment a ON a.id = m.testId
            WHERE m.tenantId = :tenantId AND m.studentId = :studentId
            ORDER BY a.testDate DESC
            """)
    List<Mark> findByTenantIdAndStudentIdOrderByTestDateDesc(
            @Param("tenantId") UUID tenantId,
            @Param("studentId") UUID studentId);

    /**
     * Every graded score in a batch within a date range, paired with its
     * test's max marks so ReportService can average marksObtained/maxMarks
     * per entry in Java rather than doing that division in HQL (mixing
     * BigDecimal division into the query itself is easy to get subtly
     * wrong with rounding - safer done with BigDecimal/double in Java).
     */
    @Query("""
            SELECT m.marksObtained AS marksObtained, a.maxMarks AS maxMarks
            FROM Mark m JOIN Assessment a ON a.id = m.testId
            WHERE m.tenantId = :tenantId AND a.batchId = :batchId AND m.marksObtained IS NOT NULL
            AND a.testDate BETWEEN :from AND :to
            """)
    List<MarkScore> findScoresInRange(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    interface MarkScore {
        BigDecimal getMarksObtained();

        BigDecimal getMaxMarks();
    }
}
