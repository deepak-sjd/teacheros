package com.teacheros.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, UUID> {

    Optional<TestAttempt> findByTenantIdAndTestIdAndStudentId(UUID tenantId, UUID testId, UUID studentId);

    Optional<TestAttempt> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Every attempt (in progress or submitted) for a test - used by
     * OnlineTestService#results.
     */
    List<TestAttempt> findByTenantIdAndTestId(UUID tenantId, UUID testId);
}
