package com.teacheros.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TestAnswerRepository extends JpaRepository<TestAnswer, UUID> {

    List<TestAnswer> findByTenantIdAndAttemptId(UUID tenantId, UUID attemptId);

    /**
     * Every answer across a set of attempts - used by OnlineTestService#results to
     * build per-question stats.
     */
    List<TestAnswer> findByTenantIdAndAttemptIdIn(UUID tenantId, List<UUID> attemptIds);

    /**
     * Bulk delete used by AssessmentService#delete. A JPQL delete (not a derived
     * deleteBy...) so it runs immediately, before the attempts/questions rows it
     * references are removed - no dependence on Hibernate's flush ordering.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM TestAnswer a WHERE a.tenantId = :tenantId AND a.attemptId IN :attemptIds")
    int deleteForAttempts(@Param("tenantId") UUID tenantId, @Param("attemptIds") List<UUID> attemptIds);
}
