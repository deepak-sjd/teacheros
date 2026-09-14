package com.teacheros.modules.fee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeRepository extends JpaRepository<Fee, UUID> {

    Optional<Fee> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Fee> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);

    Page<Fee> findByTenantIdAndStatusIn(UUID tenantId, List<FeeStatus> statuses, Pageable pageable);
}
