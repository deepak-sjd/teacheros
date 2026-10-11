package com.teacheros.modules.permission;

import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Postgres + real Flyway (V100 included). Covers: the seeded defaults per
 * role, tenant isolation for role_permissions (the new tenant-owned table),
 * and seedDefaults idempotency.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RolePermissionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RolePermissionRepository repository;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    JdbcTemplate jdbc;

    private UUID newTenant(String name) {
        return tenantRepository.saveAndFlush(new Tenant(name)).getId();
    }

    private Set<String> defaultsFor(String role) {
        return jdbc.queryForList("SELECT permission FROM role_permission_defaults WHERE role = ?",
                String.class, role).stream().collect(Collectors.toSet());
    }

    @Test
    void everySeededDefaultIsAKnownPermission() {
        Set<String> known = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        List<String> seeded = jdbc.queryForList("SELECT DISTINCT permission FROM role_permission_defaults",
                String.class);

        assertTrue(known.containsAll(seeded), "seed has a permission the enum doesn't: " + seeded);
        assertEquals(known, defaultsFor("OWNER"), "OWNER must have every permission");
    }

    @Test
    void existingStaffRolesKeepEverythingTheyHadExceptNewLeadsCapability() {
        Set<String> all = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> allButLeads = all.stream().filter(p -> !p.equals("LEADS_MANAGE")).collect(Collectors.toSet());

        assertEquals(all, defaultsFor("ADMIN"));
        assertEquals(all, defaultsFor("STAFF"));
        assertEquals(allButLeads, defaultsFor("TEACHER"));
    }

    @Test
    void accountantGetsFeesAndReportsButNoMarksHomeworkOrAttendance() {
        Set<String> accountant = defaultsFor("ACCOUNTANT");

        assertEquals(Set.of("FEES_VIEW", "FEES_EDIT", "REPORTS_VIEW", "STUDENTS_VIEW", "BATCHES_VIEW"), accountant);
        assertFalse(accountant.contains("MARKS_EDIT"));
        assertFalse(accountant.contains("MARKS_VIEW_ALL"));
        assertFalse(accountant.contains("HOMEWORK_EDIT"));
    }

    @Test
    void counsellorGetsLeadsButNoFees() {
        Set<String> counsellor = defaultsFor("COUNSELLOR");

        assertTrue(counsellor.contains("LEADS_MANAGE"));
        assertFalse(counsellor.contains("FEES_VIEW"));
        assertFalse(counsellor.contains("FEES_EDIT"));
    }

    @Test
    void seedingOneTenantNeverLeaksIntoAnother() {
        UUID tenantA = newTenant("Tenant A");
        UUID tenantB = newTenant("Tenant B");

        repository.seedDefaults(tenantA);

        assertTrue(repository.existsByTenantIdAndRoleAndPermission(tenantA, "ACCOUNTANT", "FEES_EDIT"));
        assertFalse(repository.existsByTenantIdAndRoleAndPermission(tenantA, "ACCOUNTANT", "MARKS_EDIT"));
        // Tenant B was never seeded: nothing visible, even for the same
        // role/permission.
        assertFalse(repository.existsByTenantIdAndRoleAndPermission(tenantB, "ACCOUNTANT", "FEES_EDIT"));
    }

    @Test
    void seedDefaultsIsIdempotent() {
        UUID tenant = newTenant("Tenant C");

        repository.seedDefaults(tenant);
        long first = jdbc.queryForObject("SELECT count(*) FROM role_permissions WHERE tenant_id = ?", Long.class,
                tenant);
        repository.seedDefaults(tenant);
        long second = jdbc.queryForObject("SELECT count(*) FROM role_permissions WHERE tenant_id = ?", Long.class,
                tenant);

        assertTrue(first > 0);
        assertEquals(first, second);
    }
}
