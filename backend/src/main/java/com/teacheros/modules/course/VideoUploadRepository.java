package com.teacheros.modules.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoUploadRepository extends JpaRepository<VideoUpload, UUID> {

    Optional<VideoUpload> findByIdAndTenantId(UUID id, UUID tenantId);

    List<VideoUpload> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<VideoUpload> findByTenantIdAndProviderAndVideoRef(UUID tenantId, String provider, String videoRef);

    /**
     * Stored bytes this tenant is using, for the storage cap. FAILED uploads
     * are excluded: they hold nothing at the provider.
     */
    @Query("""
            SELECT COALESCE(SUM(u.sizeBytes), 0) FROM VideoUpload u
            WHERE u.tenantId = :tenantId
            AND u.status <> com.teacheros.modules.course.VideoUploadStatus.FAILED
            """)
    long sumActiveBytes(@Param("tenantId") UUID tenantId);
}
