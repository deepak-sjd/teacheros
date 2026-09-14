package com.teacheros.modules.material;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, UUID> {

    Optional<StudyMaterial> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<StudyMaterial> findByTenantIdAndBatchIdOrderByCreatedAtDesc(UUID tenantId, UUID batchId, Pageable pageable);
}
