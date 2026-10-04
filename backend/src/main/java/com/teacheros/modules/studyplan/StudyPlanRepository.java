package com.teacheros.modules.studyplan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

    Optional<StudyPlan> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);
}
