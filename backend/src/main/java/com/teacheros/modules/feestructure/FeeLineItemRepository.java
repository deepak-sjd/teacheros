package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeeLineItemRepository extends JpaRepository<FeeLineItem, UUID> {

    List<FeeLineItem> findByTenantIdAndFeeIdIn(UUID tenantId, Collection<UUID> feeIds);
}
