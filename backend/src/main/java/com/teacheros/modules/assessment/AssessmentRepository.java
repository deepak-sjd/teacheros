package com.teacheros.modules.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Assessment> findByTenantIdAndBatchIdOrderByTestDateDesc(UUID tenantId, UUID batchId, Pageable pageable);

    // Used by ReportService - "tests conducted" per batch within a date
    // range, one count per batch rather than a single tenant-wide number,
    // since a report can be scoped to a single batch.
    long countByTenantIdAndBatchIdAndTestDateBetween(UUID tenantId, UUID batchId, LocalDate from, LocalDate to);
}
