package com.teacheros.modules.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    // ---- staff (every course, drafts included) ----
    Optional<Course> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Course> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    // ---- public (published only - never use the staff finders above from a public
    // route) ----
    List<Course> findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(UUID tenantId);

    Optional<Course> findByIdAndTenantIdAndPublishedTrue(UUID id, UUID tenantId);
}
