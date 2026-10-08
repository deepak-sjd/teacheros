package com.teacheros.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestAnswerRepository extends JpaRepository<TestAnswer, UUID> {

    List<TestAnswer> findByTenantIdAndAttemptId(UUID tenantId, UUID attemptId);

    /**
     * Every answer across a set of attempts - used by OnlineTestService#results to
     * build per-question stats.
     */
    List<TestAnswer> findByTenantIdAndAttemptIdIn(UUID tenantId, List<UUID> attemptIds);
}
