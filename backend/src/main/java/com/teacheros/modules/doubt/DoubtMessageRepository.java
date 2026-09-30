package com.teacheros.modules.doubt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DoubtMessageRepository extends JpaRepository<DoubtMessage, UUID> {

    List<DoubtMessage> findByTenantIdAndStudentIdOrderByCreatedAtAsc(UUID tenantId, UUID studentId);

    /**
     * Most recent messages for a student, newest first - used to build a
     * bounded conversation-history window to send to the AI provider
     * without loading (and paying token cost for) the entire thread on a
     * student who has been using this for months.
     */
    List<DoubtMessage> findTop10ByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId);

    long countByTenantIdAndStudentIdAndSenderAndCreatedAtGreaterThanEqual(
            UUID tenantId, UUID studentId, DoubtSender sender, Instant since);
}
