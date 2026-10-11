package com.teacheros.modules.timetable;

import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.tenant.Tenant;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves class_join_logs can't leak across tenants at the repository layer:
 * every finder the service uses returns nothing for another tenant's ID,
 * even for a row that genuinely exists. Runs the real Flyway migrations
 * (V71 included) against a real Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ClassJoinLogTenantIsolationTest {

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
    ClassJoinLogRepository repository;

    @Test
    void joinLogsAreInvisibleToOtherTenants() {
        Tenant tenantA = em.persist(new Tenant("Tenant A"));
        Tenant tenantB = em.persist(new Tenant("Tenant B"));
        Batch batch = em.persist(new Batch(tenantA.getId(), "Batch", "Maths", null));
        Student student = em.persist(
                new Student(tenantA.getId(), batch.getId(), "Asha", "8", "+919000000002"));
        TimetableSlot slot = em.persist(new TimetableSlot(tenantA.getId(), batch.getId(), 1,
                LocalTime.of(16, 0), LocalTime.of(17, 0), null));

        LocalDate date = LocalDate.now();
        em.persistAndFlush(new ClassJoinLog(tenantA.getId(), slot.getId(), student.getId(), date));

        // Sanity: the owning tenant sees it through both finders.
        assertTrue(repository.findByTenantIdAndTimetableSlotIdAndStudentIdAndSessionDate(
                tenantA.getId(), slot.getId(), student.getId(), date).isPresent());
        assertEquals(1, repository.findByTenantIdAndTimetableSlotIdAndSessionDateOrderByJoinedAtAsc(
                tenantA.getId(), slot.getId(), date).size());

        // Another tenant sees nothing, through both finders.
        assertTrue(repository.findByTenantIdAndTimetableSlotIdAndStudentIdAndSessionDate(
                tenantB.getId(), slot.getId(), student.getId(), date).isEmpty());
        assertTrue(repository.findByTenantIdAndTimetableSlotIdAndSessionDateOrderByJoinedAtAsc(
                tenantB.getId(), slot.getId(), date).isEmpty());
    }
}
