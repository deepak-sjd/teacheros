package com.teacheros.modules.course;

import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CourseRepositoryTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CourseRepository courseRepository;
    @Autowired
    TenantRepository tenantRepository;

    @Test
    void tenantsAreIsolated_andPublicFindersNeverReturnDrafts() {
        Tenant a = tenantRepository.save(new Tenant("School A"));
        Tenant b = tenantRepository.save(new Tenant("School B"));

        Course live = new Course(a.getId(), "Live course", new BigDecimal("999"));
        live.setPublished(true);
        live = courseRepository.save(live);
        Course draft = courseRepository.save(new Course(a.getId(), "Draft course", new BigDecimal("500")));

        // Another tenant sees nothing, by id or by list.
        assertTrue(courseRepository.findByIdAndTenantId(live.getId(), b.getId()).isEmpty());
        assertTrue(courseRepository.findByTenantIdOrderByCreatedAtDesc(b.getId()).isEmpty());
        assertTrue(courseRepository.findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(b.getId()).isEmpty());
        assertTrue(courseRepository.findByIdAndTenantIdAndPublishedTrue(live.getId(), b.getId()).isEmpty());

        // Own tenant: staff finders see both, public finders only the published one.
        assertEquals(2, courseRepository.findByTenantIdOrderByCreatedAtDesc(a.getId()).size());
        assertEquals(1, courseRepository.findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(a.getId()).size());
        assertTrue(courseRepository.findByIdAndTenantIdAndPublishedTrue(draft.getId(), a.getId()).isEmpty());
        assertTrue(courseRepository.findByIdAndTenantIdAndPublishedTrue(live.getId(), a.getId()).isPresent());
    }
}
