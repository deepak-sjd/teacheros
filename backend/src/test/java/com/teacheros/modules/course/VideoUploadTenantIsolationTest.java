package com.teacheros.modules.course;

import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves video_uploads can't leak across tenants at the repository layer, and
 * that the storage-cap sum counts only this tenant's non-failed videos. Runs
 * the real Flyway migrations (V73 included) against a real Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class VideoUploadTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestEntityManager em;
    @Autowired
    VideoUploadRepository repository;

    private VideoUpload persist(Tenant tenant, User user, String ref, long size, VideoUploadStatus status) {
        VideoUpload u = new VideoUpload(tenant.getId(), "bunny", ref, "Video " + ref, size, user.getId());
        u.setStatus(status);
        return em.persistAndFlush(u);
    }

    @Test
    void videosAreInvisibleToOtherTenants_andTheStorageSumIsPerTenantAndSkipsFailed() {
        Tenant a = em.persist(new Tenant("Tenant A"));
        Tenant b = em.persist(new Tenant("Tenant B"));
        User user = em.persist(new User(a.getId(), "+919000000001", "Owner", Role.OWNER));

        VideoUpload aReady = persist(a, user, "a-ready", 100, VideoUploadStatus.READY);
        persist(a, user, "a-processing", 50, VideoUploadStatus.PROCESSING);
        persist(a, user, "a-failed", 500, VideoUploadStatus.FAILED);
        VideoUpload bReady = persist(b, user, "b-ready", 1000, VideoUploadStatus.READY);

        // Sanity: the owning tenant sees its own.
        assertTrue(repository.findByIdAndTenantId(aReady.getId(), a.getId()).isPresent());
        assertTrue(repository.findByTenantIdAndProviderAndVideoRef(a.getId(), "bunny", "a-ready").isPresent());
        assertEquals(3, repository.findByTenantIdOrderByCreatedAtDesc(a.getId()).size());

        // Another tenant sees nothing of A's, through every finder.
        assertTrue(repository.findByIdAndTenantId(aReady.getId(), b.getId()).isEmpty());
        assertTrue(repository.findByTenantIdAndProviderAndVideoRef(b.getId(), "bunny", "a-ready").isEmpty());
        assertEquals(1, repository.findByTenantIdOrderByCreatedAtDesc(b.getId()).size());
        assertTrue(repository.findByIdAndTenantId(bReady.getId(), a.getId()).isEmpty());

        // Storage cap input: FAILED excluded, other tenants excluded.
        assertEquals(150L, repository.sumActiveBytes(a.getId()));
        assertEquals(1000L, repository.sumActiveBytes(b.getId()));
    }

    @Test
    void aTenantWithNoVideosHasZeroUsage() {
        Tenant c = em.persist(new Tenant("Tenant C"));
        assertEquals(0L, repository.sumActiveBytes(c.getId()));
    }
}
