package com.teacheros.modules.reportcard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamTermRepository extends JpaRepository<ExamTerm, UUID> {

    Optional<ExamTerm> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ExamTerm> findByTenantIdOrderByStartDateAsc(UUID tenantId);
}
