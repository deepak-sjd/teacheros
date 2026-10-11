package com.teacheros.modules.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    Optional<Chapter> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Chapter> findByTenantIdAndCourseIdOrderByPositionAsc(UUID tenantId, UUID courseId);
}
