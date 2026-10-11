package com.teacheros.modules.ai;

import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AiUsageRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    AiUsageRepository aiUsageRepository;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;

    @Test
    void counts_andLookups_areScopedToTheTenant() {
        Tenant tenantA = tenantRepository.save(new Tenant("School A"));
        Tenant tenantB = tenantRepository.save(new Tenant("School B"));
        User userA = userRepository.save(new User(tenantA.getId(), "+919000000001", "Owner A", Role.OWNER));
        User userB = userRepository.save(new User(tenantB.getId(), "+919000000002", "Owner B", Role.OWNER));

        AiUsage rowA1 = aiUsageRepository.save(
                new AiUsage(tenantA.getId(), userA.getId(), AiFeature.STUDY_PLAN, "log", null));
        aiUsageRepository.save(new AiUsage(tenantA.getId(), userA.getId(), AiFeature.STUDY_PLAN, "log", null));
        aiUsageRepository.save(new AiUsage(tenantB.getId(), userB.getId(), AiFeature.STUDY_PLAN, "log", null));
        aiUsageRepository.flush();

        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);

        assertEquals(2, aiUsageRepository.countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(
                tenantA.getId(), AiFeature.STUDY_PLAN, since));
        assertEquals(1, aiUsageRepository.countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(
                tenantB.getId(), AiFeature.STUDY_PLAN, since));
        assertEquals(0, aiUsageRepository.countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(
                tenantB.getId(), AiFeature.DOUBT, since));

        assertTrue(aiUsageRepository.findByIdAndTenantId(rowA1.getId(), tenantA.getId()).isPresent());
        // Another tenant's id must never resolve a row it doesn't own.
        assertTrue(aiUsageRepository.findByIdAndTenantId(rowA1.getId(), tenantB.getId()).isEmpty());
    }

    @Test
    void advisoryLockQuery_runsAgainstRealPostgres() {
        assertEquals(1, aiUsageRepository.lockForReservation("some-tenant:STUDY_PLAN"));
    }
}
