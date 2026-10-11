package com.teacheros.modules.feestructure;

import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs V1..V111 against a real Postgres (so it also proves the entities match
 * the migrations under ddl-auto=validate), then checks every new
 * tenant-owned table: tenant A's rows are invisible to tenant B's finders.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FeeStructureTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired FeeHeadRepository heads;
    @Autowired InstalmentPlanRepository plans;
    @Autowired FeeStructureRepository structures;
    @Autowired FeeDiscountRepository discounts;
    @Autowired FeeRulesRepository rules;

    @Test
    void tenantBCannotSeeTenantAsFeeSetupOrDiscounts() {
        UUID a = tenants.save(new Tenant("Tenant A")).getId();
        UUID b = tenants.save(new Tenant("Tenant B")).getId();

        FeeHead head = heads.save(new FeeHead(a, "Tuition", null, true, 0));
        InstalmentPlan plan = plans.save(new InstalmentPlan(a, "Annual", 1));
        FeeStructure structure = structures.save(new FeeStructure(a, "Class 8", "2026-27", "8", null, plan.getId()));
        UUID owner = users.save(new User(a, "+919111111111", "Owner A", Role.OWNER)).getId();
        UUID student = students.save(new Student(a, null, "Asha", "8", "+919222222222")).getId();
        FeeDiscount discount = discounts.save(new FeeDiscount(a, student, null, AmountType.PERCENT,
                new BigDecimal("10.00"), "Merit", owner));
        rules.save(new FeeRules(a));

        // Tenant A sees its own rows...
        assertThat(heads.findByIdAndTenantId(head.getId(), a)).isPresent();
        assertThat(plans.findByIdAndTenantId(plan.getId(), a)).isPresent();
        assertThat(structures.findByIdAndTenantId(structure.getId(), a)).isPresent();
        assertThat(discounts.findByIdAndTenantId(discount.getId(), a)).isPresent();
        assertThat(rules.findById(a)).isPresent();

        // ...tenant B sees none of them.
        assertThat(heads.findByIdAndTenantId(head.getId(), b)).isEmpty();
        assertThat(heads.findByTenantIdOrderBySortOrderAscNameAsc(b)).isEmpty();
        assertThat(plans.findByIdAndTenantId(plan.getId(), b)).isEmpty();
        assertThat(structures.findByIdAndTenantId(structure.getId(), b)).isEmpty();
        assertThat(structures.findByTenantIdOrderByAcademicYearDescNameAsc(b)).isEmpty();
        assertThat(discounts.findByIdAndTenantId(discount.getId(), b)).isEmpty();
        assertThat(discounts.findByTenantIdOrderByCreatedAtDesc(b)).isEmpty();
        assertThat(rules.findById(b)).isEmpty();
        assertThat(heads.findByTenantIdAndIdIn(b, java.util.List.of(head.getId()))).isEmpty();
    }

    @Test
    void sameHeadNameIsAllowedInTwoTenants() {
        UUID a = tenants.save(new Tenant("T1")).getId();
        UUID b = tenants.save(new Tenant("T2")).getId();
        heads.saveAndFlush(new FeeHead(a, "Tuition", null, true, 0));
        heads.saveAndFlush(new FeeHead(b, "Tuition", null, true, 0));
        assertThat(heads.existsByTenantIdAndNameIgnoreCase(a, "tuition")).isTrue();
        assertThat(heads.findByTenantIdOrderBySortOrderAscNameAsc(b)).hasSize(1);
    }
}
