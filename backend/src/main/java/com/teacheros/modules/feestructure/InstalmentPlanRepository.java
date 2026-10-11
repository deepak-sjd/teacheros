package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstalmentPlanRepository extends JpaRepository<InstalmentPlan, UUID> {

    Optional<InstalmentPlan> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InstalmentPlan> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<InstalmentPlan> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
