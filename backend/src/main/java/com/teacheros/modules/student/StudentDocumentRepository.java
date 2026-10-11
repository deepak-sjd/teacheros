package com.teacheros.modules.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentDocumentRepository extends JpaRepository<StudentDocument, UUID> {

    List<StudentDocument> findByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId);

    // Scoped by student as well as tenant, so a document id from one
    // student can never be fetched through another student's URL.
    Optional<StudentDocument> findByIdAndTenantIdAndStudentId(UUID id, UUID tenantId, UUID studentId);
}
