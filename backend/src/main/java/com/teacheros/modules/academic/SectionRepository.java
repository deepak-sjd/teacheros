package com.teacheros.modules.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    Optional<Section> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Section> findByTenantId(UUID tenantId);

    List<Section> findByTenantIdAndClassId(UUID tenantId, UUID classId);

    long countByTenantIdAndClassIdAndActiveTrue(UUID tenantId, UUID classId);

    boolean existsByTenantIdAndClassIdAndNameIgnoreCase(UUID tenantId, UUID classId, String name);

    boolean existsByTenantIdAndClassIdAndNameIgnoreCaseAndIdNot(UUID tenantId, UUID classId, String name, UUID id);
}
