package com.teacheros.modules.announcement;

import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest (not @SpringBootTest) so the S3/MinIO startup runner in
// StorageConfig isn't pulled in. Flyway still runs every migration.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AnnouncementTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TenantRepository tenants;
    @Autowired
    UserRepository users;
    @Autowired
    AnnouncementRepository announcements;

    @Test
    void anotherTenantCanNeverSeeAnnouncement() {
        Tenant a = tenants.save(new Tenant("Tenant A"));
        Tenant b = tenants.save(new Tenant("Tenant B"));
        User teacherA = users.save(new User(a.getId(), "+911000000001", "Teacher A", Role.OWNER));
        Announcement ann = announcements.save(new Announcement(a.getId(), "Holiday", "Closed Monday",
                AnnouncementAudience.ALL, null, null, teacherA.getId(), "Teacher A"));

        var page = PageRequest.of(0, 10);
        List<UUID> anyBatch = List.of(new UUID(0, 0));
        // Same placeholder AnnouncementService uses; Postgres rejects NUL (\u0000) in
        // strings.
        List<String> anyGrade = List.of("__no_grade__");

        assertThat(announcements.findByIdAndTenantId(ann.getId(), b.getId())).isEmpty();
        assertThat(announcements.findByTenantIdOrderByCreatedAtDesc(b.getId(), page)).isEmpty();
        assertThat(announcements.findVisible(b.getId(), anyBatch, anyGrade, page)).isEmpty();

        assertThat(announcements.findByIdAndTenantId(ann.getId(), a.getId())).isPresent();
        assertThat(announcements.findVisible(a.getId(), anyBatch, anyGrade, page)).hasSize(1);
    }
}
