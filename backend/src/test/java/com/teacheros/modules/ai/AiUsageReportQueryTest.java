package com.teacheros.modules.ai;

import com.teacheros.modules.ai.AiUsageRepository.DailyUsageRow;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AiUsageReportQueryTest {

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
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aggregate_isTenantScoped_andBucketsDaysInIst() {
        Tenant a = tenantRepository.save(new Tenant("School A"));
        Tenant b = tenantRepository.save(new Tenant("School B"));
        User ua = userRepository.save(new User(a.getId(), "+919000000011", "A", Role.OWNER));
        User ub = userRepository.save(new User(b.getId(), "+919000000012", "B", Role.OWNER));

        // 19:00Z on 9 Mar == 00:30 IST on 10 Mar; 18:00Z on 9 Mar == 23:30 IST on 9
        // Mar.
        insert(a.getId(), ua.getId(), "STUDY_PLAN", "SUCCESS", "2026-03-09T19:00:00Z", 10, 20);
        insert(a.getId(), ua.getId(), "STUDY_PLAN", "FAILED", "2026-03-09T18:00:00Z", null, null);
        insert(b.getId(), ub.getId(), "STUDY_PLAN", "SUCCESS", "2026-03-09T19:00:00Z", 999, 999);

        List<DailyUsageRow> rows = aiUsageRepository.aggregateByDay(a.getId(),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"));

        assertEquals(2, rows.size()); // tenant B's row never appears
        DailyUsageRow tenth = rows.stream().filter(r -> r.getDay().equals("2026-03-10")).findFirst().orElseThrow();
        assertEquals("SUCCESS", tenth.getStatus());
        assertEquals(10L, tenth.getInputTokens());
        assertEquals(20L, tenth.getOutputTokens());
        DailyUsageRow ninth = rows.stream().filter(r -> r.getDay().equals("2026-03-09")).findFirst().orElseThrow();
        assertEquals("FAILED", ninth.getStatus());
        assertEquals(0L, ninth.getInputTokens()); // COALESCE(SUM(null), 0)
    }

    private void insert(java.util.UUID tenantId, java.util.UUID userId, String feature, String status,
            String createdAt, Integer in, Integer out) {
        jdbc.update(
                """
                        INSERT INTO ai_usage (tenant_id, user_id, feature, provider, status, input_tokens, output_tokens, created_at)
                        VALUES (?, ?, ?, 'anthropic', ?, ?, ?, ?::timestamptz)
                        """,
                tenantId, userId, feature, status, in, out, createdAt);
    }
}
