package com.teacheros.modules.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, UUID> {

    List<AnnouncementRead> findByTenantIdAndAnnouncementId(UUID tenantId, UUID announcementId);

    List<AnnouncementRead> findByTenantIdAndUserIdAndAnnouncementIdIn(
            UUID tenantId, UUID userId, Collection<UUID> announcementIds);

    // ON CONFLICT DO NOTHING: two taps / two tabs racing can't throw a
    // unique-violation (which would poison the surrounding transaction).
    @Modifying
    @Query(value = """
            INSERT INTO announcement_reads (id, tenant_id, announcement_id, user_id, read_at)
            VALUES (gen_random_uuid(), :tenantId, :announcementId, :userId, now())
            ON CONFLICT (announcement_id, user_id) DO NOTHING
            """, nativeQuery = true)
    void markRead(@Param("tenantId") UUID tenantId, @Param("announcementId") UUID announcementId,
            @Param("userId") UUID userId);
}
