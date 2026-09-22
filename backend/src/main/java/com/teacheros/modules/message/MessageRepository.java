package com.teacheros.modules.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByTenantIdAndStudentIdOrderByCreatedAtAsc(UUID tenantId, UUID studentId);

    long countByTenantIdAndStudentIdAndReadByStaffFalse(UUID tenantId, UUID studentId);

    long countByTenantIdAndStudentIdAndReadByParentFalse(UUID tenantId, UUID studentId);

    @Modifying
    @Query("""
            UPDATE Message m SET m.readByStaff = true
            WHERE m.tenantId = :tenantId AND m.studentId = :studentId AND m.readByStaff = false
            """)
    void markReadByStaff(@Param("tenantId") UUID tenantId, @Param("studentId") UUID studentId);

    @Modifying
    @Query("""
            UPDATE Message m SET m.readByParent = true
            WHERE m.tenantId = :tenantId AND m.studentId = :studentId AND m.readByParent = false
            """)
    void markReadByParent(@Param("tenantId") UUID tenantId, @Param("studentId") UUID studentId);
}
