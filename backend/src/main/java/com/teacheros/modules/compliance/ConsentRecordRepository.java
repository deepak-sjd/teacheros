package com.teacheros.modules.compliance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByTenantIdAndStudentIdIn(UUID tenantId, Collection<UUID> studentIds);

    Optional<ConsentRecord> findFirstByTenantIdAndStudentIdAndPurposeAndWithdrawnAtIsNull(
            UUID tenantId, UUID studentId, String purpose);
}
