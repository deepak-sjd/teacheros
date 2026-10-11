package com.teacheros.modules.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    Optional<Lesson> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Lesson> findByTenantIdAndCourseId(UUID tenantId, UUID courseId);

    List<Lesson> findByTenantIdAndChapterIdOrderByPositionAsc(UUID tenantId, UUID chapterId);
}
