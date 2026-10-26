package com.teacheros.modules.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    Optional<SchoolClass> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SchoolClass> findByTenantIdOrderByDisplayOrderAscNameAsc(UUID tenantId);

    List<SchoolClass> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
