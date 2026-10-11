package com.teacheros.modules.reportcard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportCardRemarkRepository extends JpaRepository<ReportCardRemark, UUID> {

    List<ReportCardRemark> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);

    List<ReportCardRemark> findByTenantIdAndExamTermId(UUID tenantId, UUID examTermId);

    Optional<ReportCardRemark> findByTenantIdAndExamTermIdAndStudentId(UUID tenantId, UUID examTermId,
            UUID studentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ReportCardRemark r WHERE r.tenantId = :tenantId AND r.examTermId = :termId")
    int deleteForTerm(@Param("tenantId") UUID tenantId, @Param("termId") UUID termId);
}
