package com.teacheros.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
