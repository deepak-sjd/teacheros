package com.teacheros.modules.announcement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    Optional<Announcement> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Announcement> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Announcements a parent/student may see: ALL, plus BATCH ones for any of
     * their linked students' batches, plus CLASS ones matching any linked
     * student's grade (compared lower-cased/trimmed). Callers must pass
     * non-empty collections - AnnouncementService supplies sentinels - since
     * "IN ()" with an empty list is not portable.
     */
    @Query("""
            SELECT a FROM Announcement a
            WHERE a.tenantId = :tenantId
            AND (a.audience = com.teacheros.modules.announcement.AnnouncementAudience.ALL
                 OR (a.audience = com.teacheros.modules.announcement.AnnouncementAudience.BATCH
                     AND a.batchId IN :batchIds)
                 OR (a.audience = com.teacheros.modules.announcement.AnnouncementAudience.CLASS
                     AND lower(trim(a.grade)) IN :grades))
            ORDER BY a.createdAt DESC
            """)
    Page<Announcement> findVisible(@Param("tenantId") UUID tenantId,
            @Param("batchIds") Collection<UUID> batchIds,
            @Param("grades") Collection<String> grades,
            Pageable pageable);
}
