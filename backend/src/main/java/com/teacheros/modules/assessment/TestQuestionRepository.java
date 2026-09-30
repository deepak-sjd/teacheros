package com.teacheros.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestQuestionRepository extends JpaRepository<TestQuestion, UUID> {

    List<TestQuestion> findByTenantIdAndTestIdOrderByPositionAsc(UUID tenantId, UUID testId);

    long countByTenantIdAndTestId(UUID tenantId, UUID testId);

    void deleteByTenantIdAndTestId(UUID tenantId, UUID testId);
}
