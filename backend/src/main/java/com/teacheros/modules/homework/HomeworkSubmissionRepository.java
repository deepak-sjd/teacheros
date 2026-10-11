package com.teacheros.modules.homework;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {

    Optional<HomeworkSubmission> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<HomeworkSubmission> findByTenantIdAndHomeworkIdAndStudentId(
            UUID tenantId, UUID homeworkId, UUID studentId);

    List<HomeworkSubmission> findByTenantIdAndHomeworkId(UUID tenantId, UUID homeworkId);
}
