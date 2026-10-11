package com.teacheros.modules.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlugAndActiveTrue(String slug);

    // Used by ReminderScheduler - the one place that legitimately iterates
    // every tenant, since a scheduled job has no request/JWT to scope by.
    List<Tenant> findAllByActiveTrue();
}
