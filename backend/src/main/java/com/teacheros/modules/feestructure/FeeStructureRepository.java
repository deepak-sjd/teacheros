package com.teacheros.modules.feestructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {

    Optional<FeeStructure> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FeeStructure> findByTenantIdOrderByAcademicYearDescNameAsc(UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCaseAndAcademicYear(UUID tenantId, String name, String academicYear);
}
