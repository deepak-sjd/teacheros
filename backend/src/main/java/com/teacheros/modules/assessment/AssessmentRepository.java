package com.teacheros.modules.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Assessment> findByTenantIdAndBatchIdOrderByTestDateDesc(UUID tenantId, UUID batchId, Pageable pageable);
}
