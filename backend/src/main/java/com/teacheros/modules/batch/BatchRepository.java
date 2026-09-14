package com.teacheros.modules.batch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    // Every finder takes tenantId explicitly. There is deliberately no
    // findById(id) usage anywhere in the service layer for tenant-owned
    // entities - always findByIdAndTenantId, so a request for another
    // tenant's resource ID returns "not found," not someone else's data.
    Optional<Batch> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Batch> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);
}
