package com.teacheros.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    /**
     * One row per (IST day, feature, provider, status) for the owner's AI usage
     * page. Grouped in SQL so the page never loads raw rows; day is returned as
     * text (YYYY-MM-DD) to avoid driver-specific date mapping.
     */
    @Query(value = """
            SELECT to_char(timezone('Asia/Kolkata', created_at), 'YYYY-MM-DD') AS "day",
                   feature AS "feature",
                   provider AS "provider",
                   status AS "status",
                   COUNT(*) AS "calls",
                   COALESCE(SUM(input_tokens), 0) AS "inputTokens",
                   COALESCE(SUM(output_tokens), 0) AS "outputTokens"
            FROM ai_usage
            WHERE tenant_id = :tenantId AND created_at >= :from AND created_at < :to
            GROUP BY 1, 2, 3, 4
            ORDER BY 1
            """, nativeQuery = true)
    List<DailyUsageRow> aggregateByDay(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    interface DailyUsageRow {
        String getDay();

        String getFeature();

        String getProvider();

        String getStatus();

        Long getCalls();

        Long getInputTokens();

        Long getOutputTokens();
    }
}
