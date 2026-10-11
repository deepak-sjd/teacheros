package com.teacheros.modules.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByTenantIdAndLessonIdAndStudentId(UUID tenantId, UUID lessonId, UUID studentId);

    List<LessonProgress> findByTenantIdAndStudentIdAndLessonIdIn(
            UUID tenantId, UUID studentId, Collection<UUID> lessonIds);
}
