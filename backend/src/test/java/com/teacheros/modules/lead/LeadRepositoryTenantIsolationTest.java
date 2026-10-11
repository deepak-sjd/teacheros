package com.teacheros.modules.lead;

import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LeadRepositoryTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    LeadRepository leadRepository;
    @Autowired
    TenantRepository tenantRepository;

    @Test
    void anotherTenantCannotSeeALead_byIdListOrCounts() {
        Tenant a = tenantRepository.save(new Tenant("School A"));
        Tenant b = tenantRepository.save(new Tenant("School B"));
        assertNotNull(a.getSlug()); // slug is generated on persist

        Lead lead = leadRepository.save(
                new Lead(a.getId(), "Asha", "+919876543210", null, null, LeadSource.WEBSITE));

        assertTrue(leadRepository.findByIdAndTenantId(lead.getId(), b.getId()).isEmpty());
        assertEquals(0, leadRepository.search(b.getId(), null, null, PageRequest.of(0, 10)).getTotalElements());
        assertTrue(leadRepository.countByStatus(b.getId()).isEmpty());

        assertTrue(leadRepository.findByIdAndTenantId(lead.getId(), a.getId()).isPresent());
        assertEquals(1,
                leadRepository.search(a.getId(), LeadStatus.NEW, null, PageRequest.of(0, 10)).getTotalElements());
    }
}
