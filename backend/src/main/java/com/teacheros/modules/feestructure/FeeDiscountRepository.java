package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeDiscountRepository extends JpaRepository<FeeDiscount, UUID> {

    Optional<FeeDiscount> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FeeDiscount> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<FeeDiscount> findByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId);

    List<FeeDiscount> findByTenantIdAndStudentIdInAndActiveTrue(UUID tenantId, Collection<UUID> studentIds);
}
