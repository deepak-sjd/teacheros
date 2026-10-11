package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InstalmentPlanItemRepository extends JpaRepository<InstalmentPlanItem, UUID> {

    List<InstalmentPlanItem> findByTenantIdAndPlanIdOrderByInstalmentNoAsc(UUID tenantId, UUID planId);

    List<InstalmentPlanItem> findByTenantIdAndPlanIdInOrderByInstalmentNoAsc(UUID tenantId, Collection<UUID> planIds);
}
