package com.teacheros.modules.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Subject> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<Subject> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(UUID tenantId, String code, UUID id);
}
