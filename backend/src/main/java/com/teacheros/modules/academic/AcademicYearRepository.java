package com.teacheros.modules.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    Optional<AcademicYear> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AcademicYear> findByTenantIdOrderByStartDateDesc(UUID tenantId);

    Optional<AcademicYear> findByTenantIdAndCurrentTrue(UUID tenantId);
}
