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

    /**
     * Every graded score across a whole batch, all-time, with the scoring
     * student attached - the raw material AssessmentService#getStudentRank
     * groups by student and sums to rank one student against their
     * batch-mates. Unlike findScoresInRange this is never returned to a
     * caller directly (see StudentRankResponse's javadoc on why raw
     * per-student scores stay server-side).
     */
    @Query("""
            SELECT m.studentId AS studentId, m.marksObtained AS marksObtained, a.maxMarks AS maxMarks
            FROM Mark m JOIN Assessment a ON a.id = m.testId
            WHERE m.tenantId = :tenantId AND a.batchId = :batchId AND m.marksObtained IS NOT NULL
            """)
    List<StudentScore> findScoresForBatch(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId);

    /**
     * Every graded score tenant-wide (or narrowed to one batch, null =
     * every batch) in a date range, along with both possible sources of a
     * "subject" label for it - the assessment's own override and its
     * batch's subject. AnalyticsService resolves one from the other the
     * same way Assessment.subject's own javadoc describes ("most batches
     * teach one subject so this is usually left null and the batch's
     * subject is used"), since a per-subject performance chart needs that
     * resolved, not raw.
     */
    @Query("""
            SELECT a.subject AS assessmentSubject, b.subject AS batchSubject,
                   m.marksObtained AS marksObtained, a.maxMarks AS maxMarks
            FROM Mark m
            JOIN Assessment a ON a.id = m.testId
            JOIN Batch b ON b.id = a.batchId
            WHERE m.tenantId = :tenantId AND m.marksObtained IS NOT NULL
            AND a.testDate BETWEEN :from AND :to
            AND (:batchId IS NULL OR a.batchId = :batchId)
            """)
    List<SubjectScoreRow> findSubjectScoresInRange(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Every graded score for ONE student, all-time, with both possible
     * subject labels - same shape and resolution as findSubjectScoresInRange
     * above (reuses the SubjectScoreRow projection), just scoped by
     * studentId instead of batchId/date-range. Used by StudyPlanService to
     * group one student's own marks by subject, rather than aggregating
     * across a whole batch the way AnalyticsService#subjectPerformance does.
     */
    @Query("""
            SELECT a.subject AS assessmentSubject, b.subject AS batchSubject,
                   m.marksObtained AS marksObtained, a.maxMarks AS maxMarks
            FROM Mark m
            JOIN Assessment a ON a.id = m.testId
            JOIN Batch b ON b.id = a.batchId
            WHERE m.tenantId = :tenantId AND m.studentId = :studentId AND m.marksObtained IS NOT NULL
            """)
    List<SubjectScoreRow> findSubjectScoresForStudent(
            @Param("tenantId") UUID tenantId, @Param("studentId") UUID studentId);

    interface MarkScore {
        BigDecimal getMarksObtained();

        BigDecimal getMaxMarks();
    }

    interface StudentScore {
        UUID getStudentId();

        BigDecimal getMarksObtained();

        BigDecimal getMaxMarks();
    }

    interface SubjectScoreRow {
        String getAssessmentSubject();

        String getBatchSubject();

        BigDecimal getMarksObtained();

        BigDecimal getMaxMarks();
    }
}
