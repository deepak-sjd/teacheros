package com.teacheros.modules.compliance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErasureRequestRepository extends JpaRepository<ErasureRequest, UUID> {

    Optional<ErasureRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ErasureRequest> findByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId);

    Page<ErasureRequest> findByTenantIdAndStatusIn(UUID tenantId, Collection<ErasureStatus> statuses,
            Pageable pageable);

    Page<ErasureRequest> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndStudentIdAndStatusIn(UUID tenantId, UUID studentId,
            Collection<ErasureStatus> statuses);
}
