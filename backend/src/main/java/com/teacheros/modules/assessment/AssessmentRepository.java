package com.teacheros.modules.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Assessment> findByTenantIdAndBatchIdOrderByTestDateDesc(UUID tenantId, UUID batchId, Pageable pageable);

    // Used by the student "take a test" list, which only ever wants the
    // ONLINE_MCQ ones for its batch - filtering server-side rather than
    // having the client fetch everything and discard offline tests.
    Page<Assessment> findByTenantIdAndBatchIdAndTestTypeOrderByTestDateDesc(
            UUID tenantId, UUID batchId, TestType testType, Pageable pageable);

    // Used by ReportService - "tests conducted" per batch within a date
    // range, one count per batch rather than a single tenant-wide number,
    // since a report can be scoped to a single batch.
    long countByTenantIdAndBatchIdAndTestDateBetween(UUID tenantId, UUID batchId, LocalDate from, LocalDate to);

    /**
     * PARENT/STUDENT view of a batch's tests: an ONLINE_MCQ test with no
     * questions yet is a draft and must not be listed (start() would fail
     * with NO_QUESTIONS). The EXISTS lives in the query, not in Java after
     * paging, so page contents and totalElements stay consistent.
     */
    @Query("""
            SELECT a FROM Assessment a
            WHERE a.tenantId = :tenantId AND a.batchId = :batchId
            AND (a.testType <> com.teacheros.modules.assessment.TestType.ONLINE_MCQ
                 OR EXISTS (SELECT 1 FROM TestQuestion q WHERE q.tenantId = :tenantId AND q.testId = a.id))
            ORDER BY a.testDate DESC
            """)
    Page<Assessment> findVisibleToStudentsByBatch(@Param("tenantId") UUID tenantId,
            @Param("batchId") UUID batchId, Pageable pageable);

    // Same as above, narrowed to one type. Separate method rather than a
    // nullable ":testType IS NULL" parameter, which Hibernate/Postgres can't
    // always type-infer for enums.
    @Query("""
            SELECT a FROM Assessment a
            WHERE a.tenantId = :tenantId AND a.batchId = :batchId AND a.testType = :testType
            AND (a.testType <> com.teacheros.modules.assessment.TestType.ONLINE_MCQ
                 OR EXISTS (SELECT 1 FROM TestQuestion q WHERE q.tenantId = :tenantId AND q.testId = a.id))
            ORDER BY a.testDate DESC
            """)
    Page<Assessment> findVisibleToStudentsByBatchAndType(@Param("tenantId") UUID tenantId,
            @Param("batchId") UUID batchId, @Param("testType") TestType testType, Pageable pageable);
}
