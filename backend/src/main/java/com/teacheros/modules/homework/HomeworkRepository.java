package com.teacheros.modules.homework;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HomeworkRepository extends JpaRepository<Homework, UUID> {

    Optional<Homework> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Homework> findByTenantIdAndBatchIdOrderByDueDateDesc(UUID tenantId, UUID batchId, Pageable pageable);
}
