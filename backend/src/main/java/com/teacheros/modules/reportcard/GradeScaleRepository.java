package com.teacheros.modules.reportcard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GradeScaleRepository extends JpaRepository<GradeScale, UUID> {

    List<GradeScale> findByTenantIdOrderByMinPercentageDesc(UUID tenantId);

    // JPQL bulk delete so it runs immediately - the replace-all flow re-inserts
    // the same (tenant, grade) keys right after, which the unique constraint
    // would reject if the delete were deferred to flush time.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GradeScale g WHERE g.tenantId = :tenantId")
    int deleteForTenant(@Param("tenantId") UUID tenantId);
}
