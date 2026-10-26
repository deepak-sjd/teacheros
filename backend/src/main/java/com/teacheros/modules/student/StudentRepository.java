package com.teacheros.modules.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Student> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    /**
     * Name / guardian-phone search for the student picker. pattern is a
     * ready-made lowercase LIKE pattern (see StudentService#list) using
     * '!' as the escape character. Ordering is fixed here, so callers
     * must not also pass a sort.
     */
    @Query("""
            SELECT s FROM Student s
            WHERE s.tenantId = :tenantId AND s.active = true
            AND (:batchId IS NULL OR s.batchId = :batchId)
            AND (LOWER(s.fullName) LIKE :pattern ESCAPE '!' OR s.guardianPhone LIKE :pattern ESCAPE '!')
            ORDER BY s.fullName
            """)
    Page<Student> search(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            @Param("pattern") String pattern, Pageable pageable);

    /**
     * Same as search, narrowed to one section (SCHOOL tenants). A separate
     * query so the original search is untouched when no section is given.
     */
    @Query("""
            SELECT s FROM Student s
            WHERE s.tenantId = :tenantId AND s.active = true
            AND s.sectionId = :sectionId
            AND (:batchId IS NULL OR s.batchId = :batchId)
            AND (LOWER(s.fullName) LIKE :pattern ESCAPE '!' OR s.guardianPhone LIKE :pattern ESCAPE '!')
            ORDER BY s.fullName
            """)
    Page<Student> searchInSection(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            @Param("sectionId") UUID sectionId, @Param("pattern") String pattern, Pageable pageable);

    /**
     * Unsearched listing narrowed to one section. Sorting comes from the
     * Pageable, like the derived finders it sits beside.
     */
    @Query("""
            SELECT s FROM Student s
            WHERE s.tenantId = :tenantId AND s.active = true
            AND s.sectionId = :sectionId
            AND (:batchId IS NULL OR s.batchId = :batchId)
            """)
    Page<Student> listInSection(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId,
            @Param("sectionId") UUID sectionId, Pageable pageable);

    /**
     * Resolve names for a set of IDs (e.g. the students behind a page of
     * fees). Includes inactive students - a fee outlives a deactivation.
     */
    List<Student> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    /**
     * Unpaged variant used by NotificationService#generateLowAttendanceAlerts -
     * it has to walk every active student in the tenant to compute each
     * one's attendance percentage, so pagination doesn't apply there.
     */
    List<Student> findAllByTenantIdAndActiveTrue(UUID tenantId);

    Page<Student> findByTenantIdAndBatchIdAndActiveTrue(UUID tenantId, UUID batchId, Pageable pageable);

    List<Student> findByTenantIdAndBatchIdAndActiveTrue(UUID tenantId, UUID batchId);

    /**
     * Used by BatchService to refuse deactivating a batch that still has students.
     */
    long countByTenantIdAndBatchIdAndActiveTrue(UUID tenantId, UUID batchId);

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
     * Active OR inactive - the unique index uq_students_tenant_phone covers
     * both, so duplicate-phone checks on create/update must too.
     */
    Optional<Student> findFirstByTenantIdAndPhoneNumber(UUID tenantId, String phoneNumber);

    /**
     * Every student phone_number in the tenant, active or not - the unique
     * index uq_students_tenant_phone applies to all rows, so StudentImportService
     * must check against inactive students' numbers too.
     */
    @Query("SELECT s.phoneNumber FROM Student s WHERE s.tenantId = :tenantId AND s.phoneNumber IS NOT NULL")
    List<String> findAllPhoneNumbersByTenantId(@Param("tenantId") UUID tenantId);

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
