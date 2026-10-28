package com.teacheros.modules.staff;

import com.teacheros.modules.auth.RefreshToken;
import com.teacheros.modules.auth.RefreshTokenRepository;
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
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres. No new tables in this module, so this is the isolation proof
 * for the new User finders ("staff list never shows another tenant's users")
 * plus the lock query and the revoke-all-sessions update.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StaffUserRepositoryTest {

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
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired JdbcTemplate jdbc;

    private UUID tenant(String name) {
        return tenants.saveAndFlush(new Tenant(name)).getId();
    }

    private User user(UUID tenant, String phone, Role role, boolean active) {
        User u = new User(tenant, phone, "User " + phone, role);
        u.setActive(active);
        return users.saveAndFlush(u);
    }

    @Test
    void staffListAndLookupNeverCrossTenants() {
        UUID a = tenant("A");
        UUID b = tenant("B");
        User ownerA = user(a, "919000000001", Role.OWNER, true);
        user(a, "919000000002", Role.ACCOUNTANT, false); // inactive still listed
        user(a, "919000000003", Role.PARENT, true); // not staff
        User teacherB = user(b, "919000000004", Role.TEACHER, true);

        List<User> staffA = users.findByTenantIdAndRoleInOrderByDisplayNameAsc(a,
                EnumSet.of(Role.OWNER, Role.ADMIN, Role.TEACHER, Role.STAFF, Role.ACCOUNTANT, Role.COUNSELLOR));

        assertThat(staffA).extracting(User::getRole).containsExactlyInAnyOrder(Role.OWNER, Role.ACCOUNTANT);
        assertThat(staffA).extracting(User::getTenantId).containsOnly(a);

        assertThat(users.findByIdAndTenantId(ownerA.getId(), a)).isPresent();
        assertThat(users.findByIdAndTenantId(ownerA.getId(), b)).isEmpty();
        assertThat(users.findByIdAndTenantId(teacherB.getId(), a)).isEmpty();
    }

    @Test
    void lockQueryReturnsOnlyThisTenantsActiveOwners() {
        UUID a = tenant("A2");
        UUID b = tenant("B2");
        User activeOwner = user(a, "919000000011", Role.OWNER, true);
        user(a, "919000000012", Role.OWNER, false);
        user(a, "919000000013", Role.ADMIN, true);
        user(b, "919000000014", Role.OWNER, true);

        assertThat(users.lockActiveByTenantIdAndRole(a, Role.OWNER)).extracting(User::getId)
                .containsExactly(activeOwner.getId());
    }

    @Test
    void phoneChecksSeeActiveAndDeactivatedAccounts() {
        UUID a = tenant("A3");
        user(a, "919000000021", Role.TEACHER, false);
        user(a, "919000000022", Role.TEACHER, true);

        assertThat(users.existsByPhoneNumberAndActiveFalse("919000000021")).isTrue();
        assertThat(users.existsByPhoneNumberAndActiveTrue("919000000021")).isFalse();
        assertThat(users.existsByPhoneNumberAndActiveTrue("919000000022")).isTrue();
    }

    @Test
    void revokeAllForUserRevokesOnlyThatUsersLiveTokens() {
        UUID a = tenant("A4");
        User target = user(a, "919000000031", Role.ACCOUNTANT, true);
        User other = user(a, "919000000032", Role.ACCOUNTANT, true);
        refreshTokens.saveAndFlush(new RefreshToken(target.getId(), "h1", Instant.now().plusSeconds(3600)));
        refreshTokens.saveAndFlush(new RefreshToken(target.getId(), "h2", Instant.now().plusSeconds(3600)));
        refreshTokens.saveAndFlush(new RefreshToken(other.getId(), "h3", Instant.now().plusSeconds(3600)));

        int revoked = refreshTokens.revokeAllForUser(target.getId(), Instant.now());

        assertThat(revoked).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class,
                target.getId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class,
                other.getId())).isEqualTo(1L);
        // second call: nothing left to revoke
        assertThat(refreshTokens.revokeAllForUser(target.getId(), Instant.now())).isZero();
    }
}
