package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeeStructureItemRepository extends JpaRepository<FeeStructureItem, UUID> {

    List<FeeStructureItem> findByTenantIdAndStructureId(UUID tenantId, UUID structureId);

    List<FeeStructureItem> findByTenantIdAndStructureIdIn(UUID tenantId, Collection<UUID> structureIds);
}
