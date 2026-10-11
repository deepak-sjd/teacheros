package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeHeadRepository extends JpaRepository<FeeHead, UUID> {

    Optional<FeeHead> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FeeHead> findByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    List<FeeHead> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
