package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** The key is the tenant id itself, so findById(tenantId) is already tenant-scoped. */
public interface FeeRulesRepository extends JpaRepository<FeeRules, UUID> {
}
