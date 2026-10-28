package com.teacheros.modules.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // ---- staff management (Dev 11) ----

    /** Tenant-scoped lookup - another tenant's user id returns empty, never their row. */
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Active and deactivated, for the Settings -> Staff list. */
    List<User> findByTenantIdAndRoleInOrderByDisplayNameAsc(UUID tenantId, Collection<Role> roles);

    /**
     * Login resolves a phone with findFirstByPhoneNumberAndActiveTrue across ALL
     * tenants, so a second active account on the same phone would make login
     * ambiguous. Staff creation/reactivation checks this first.
     */
    boolean existsByPhoneNumberAndActiveTrue(String phoneNumber);

    /** True if this phone only has deactivated account(s) - login must not sign it up afresh. */
    boolean existsByPhoneNumberAndActiveFalse(String phoneNumber);

    /**
     * The tenant's active users in one role, row-locked so two concurrent
     * demotions/deactivations can't both see "there is another owner" and
     * leave the tenant with none. Only call inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.tenantId = :tenantId and u.role = :role and u.active = true")
    List<User> lockActiveByTenantIdAndRole(@Param("tenantId") UUID tenantId, @Param("role") Role role);
}
