package com.teacheros.modules.fee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {
    List<FeePayment> findByTenantIdAndFeeId(UUID tenantId, UUID feeId);
}
