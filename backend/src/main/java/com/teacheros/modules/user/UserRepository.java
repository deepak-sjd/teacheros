package com.teacheros.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumberAndTenantId(String phoneNumber, UUID tenantId);

    /**
     * A phone number should map to exactly one active account for login
     * purposes in the MVP (single-tenant-per-teacher-account model).
     * If/when a phone number can belong to accounts in multiple tenants
     * (e.g. a parent with children at two different tutors), the login
     * flow will need to change to disambiguate - tracked as a known
     * future requirement, not solved here.
     */
    Optional<User> findFirstByPhoneNumberAndActiveTrue(String phoneNumber);

    List<User> findByTenantIdAndActiveTrueAndRoleIn(UUID tenantId, Collection<Role> roles);
}
