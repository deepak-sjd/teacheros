package com.teacheros.modules.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Student> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    Page<Student> findByTenantIdAndBatchIdAndActiveTrue(UUID tenantId, UUID batchId, Pageable pageable);

    List<Student> findByTenantIdAndBatchIdAndActiveTrue(UUID tenantId, UUID batchId);

    /**
     * All active students in a tenant whose guardian_phone matches - i.e.
     * this guardian's children, including siblings. This is how a parent
     * account's linked children are derived: no separate parent-student
     * link table, just a query against the phone already collected when
     * the student was added.
     */
    List<Student> findByTenantIdAndGuardianPhoneAndActiveTrue(UUID tenantId, String guardianPhone);

    /**
     * The student whose own phone_number matches, scoped to a tenant - used
     * once we already know which tenant the logged-in user belongs to.
     */
    Optional<Student> findFirstByTenantIdAndPhoneNumberAndActiveTrue(UUID tenantId, String phoneNumber);

    /**
     * Used ONLY during first-login account provisioning, before we know
     * which tenant a phone number belongs to. Deliberately NOT tenant
     * scoped - see AuthService.provisionAccountForPhone for the documented
     * limitation this implies (a guardian phone matching students in more
     * than one tenant will resolve to the first match found).
     */
    Optional<Student> findFirstByGuardianPhoneAndActiveTrue(String guardianPhone);

    Optional<Student> findFirstByPhoneNumberAndActiveTrue(String phoneNumber);
}
