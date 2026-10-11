package com.teacheros.modules.feestructure;

import com.teacheros.modules.student.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped lookup by ids (active or not) - reports must still name deactivated students. */
public interface StudentLookupRepository extends JpaRepository<Student, UUID> {

    List<Student> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
