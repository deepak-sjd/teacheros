package com.teacheros.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, UUID> {

    Optional<TestAttempt> findByTenantIdAndTestIdAndStudentId(UUID tenantId, UUID testId, UUID studentId);

    Optional<TestAttempt> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Every attempt (in progress or submitted) for a test - used by
     * OnlineTestService#results and AssessmentService#delete.
     */
    List<TestAttempt> findByTenantIdAndTestId(UUID tenantId, UUID testId);

    /**
     * Bulk delete used by AssessmentService#delete, after the attempts' answers are
     * gone.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM TestAttempt a WHERE a.tenantId = :tenantId AND a.testId = :testId")
    int deleteForTest(@Param("tenantId") UUID tenantId, @Param("testId") UUID testId);
}
