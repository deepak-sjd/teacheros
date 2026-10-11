package com.teacheros.modules.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadStatusHistoryRepository extends JpaRepository<LeadStatusHistory, UUID> {
    List<LeadStatusHistory> findByTenantIdAndLeadIdOrderByChangedAtAsc(UUID tenantId, UUID leadId);
}
