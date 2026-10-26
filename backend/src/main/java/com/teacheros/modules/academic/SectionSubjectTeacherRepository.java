package com.teacheros.modules.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionSubjectTeacherRepository extends JpaRepository<SectionSubjectTeacher, UUID> {

    List<SectionSubjectTeacher> findByTenantIdAndSectionId(UUID tenantId, UUID sectionId);

    Optional<SectionSubjectTeacher> findByTenantIdAndSectionIdAndSubjectId(UUID tenantId, UUID sectionId,
            UUID subjectId);

    void deleteByTenantIdAndSectionIdAndSubjectId(UUID tenantId, UUID sectionId, UUID subjectId);
}
