package com.teacheros.modules.academic;

import com.teacheros.modules.student.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Placement queries over students. Every finder takes tenantId explicitly. */
public interface AcademicStudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Student> findByTenantIdAndSectionIdAndActiveTrue(UUID tenantId, UUID sectionId);

    /**
     * Everyone still in a given year within the given sections - the promotion
     * population.
     */
    List<Student> findByTenantIdAndAcademicYearIdAndSectionIdInAndActiveTrue(
            UUID tenantId, UUID academicYearId, Collection<UUID> sectionIds);

    long countByTenantIdAndSectionIdAndActiveTrue(UUID tenantId, UUID sectionId);

    /** Friendly pre-check; the partial unique index is the real guarantee. */
    boolean existsByTenantIdAndSectionIdAndAcademicYearIdAndRollNumberAndActiveTrueAndIdNot(
            UUID tenantId, UUID sectionId, UUID academicYearId, String rollNumber, UUID id);

    @Query("""
            SELECT s.sectionId AS sectionId, COUNT(s) AS studentCount
            FROM Student s
            WHERE s.tenantId = :tenantId AND s.active = true AND s.sectionId IS NOT NULL
            GROUP BY s.sectionId
            """)
    List<SectionCount> countActiveBySection(@Param("tenantId") UUID tenantId);

    interface SectionCount {
        UUID getSectionId();

        long getStudentCount();
    }
}
