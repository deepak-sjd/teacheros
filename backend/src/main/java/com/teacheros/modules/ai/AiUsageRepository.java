package com.teacheros.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

    Optional<AiUsage> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Every status counts - failed and abandoned calls still hit (or tried to hit)
     * the provider.
     */
    long countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(UUID tenantId, AiFeature feature, Instant since);

    /**
     * Transaction-scoped advisory lock: serialises "count, then insert" for one
     * tenant+feature so two parallel requests can't both read 99/100 and both
     * insert. Released automatically at commit/rollback.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:key, 0))", nativeQuery = true)
    Integer lockForReservation(@Param("key") String key);
}
