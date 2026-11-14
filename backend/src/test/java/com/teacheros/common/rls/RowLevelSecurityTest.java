package com.teacheros.common.security.rls;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RowLevelSecurityTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    static UUID tenantA;
    static UUID tenantB;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                Statement s = c.createStatement()) {
            s.execute("CREATE ROLE teacheros_app LOGIN PASSWORD 'app' NOSUPERUSER NOBYPASSRLS");
        }
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();

        try (Connection c = appConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.bypass_rls', 'on', false)");
            tenantA = insertTenant(s, "A");
            tenantB = insertTenant(s, "B");
            UUID studentA = insertStudent(s, tenantA, "Student A");
            insertStudent(s, tenantB, "Student B");
            s.execute("INSERT INTO fees (tenant_id, student_id, amount_due, due_date) VALUES ('" + tenantA + "', '"
                    + studentA + "', 100, now()::date)");
        }
    }

    static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(pg.getJdbcUrl(), "teacheros_app", "app");
    }

    static UUID insertTenant(Statement s, String name) throws SQLException {
        try (ResultSet rs = s.executeQuery("INSERT INTO tenants (name) VALUES ('" + name + "') RETURNING id")) {
            rs.next();
            return UUID.fromString(rs.getString(1));
        }
    }

    static UUID insertStudent(Statement s, UUID tenant, String name) throws SQLException {
        try (ResultSet rs = s.executeQuery("INSERT INTO students (tenant_id, full_name, guardian_phone) VALUES ('"
                + tenant + "', '" + name + "', '+910000000000') RETURNING id")) {
            rs.next();
            return UUID.fromString(rs.getString(1));
        }
    }

    static long count(Statement s, String table) throws SQLException {
        try (ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void unscopedQueryReturnsNoRows() throws Exception {
        try (Connection c = appConnection(); Statement s = c.createStatement()) {
            assertThat(count(s, "students")).isZero();
            assertThat(count(s, "fees")).isZero();
        }
    }

    @Test
    void emptyTenantSettingReturnsNoRows() throws Exception {
        try (Connection c = appConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.tenant_id', '', false)");
            assertThat(count(s, "students")).isZero();
        }
    }

    @Test
    void aTenantSeesOnlyItsOwnRows() throws Exception {
        try (Connection c = appConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.tenant_id', '" + tenantA + "', false)");
            assertThat(count(s, "students")).isEqualTo(1);
            assertThat(count(s, "fees")).isEqualTo(1);
            s.execute("SELECT set_config('app.tenant_id', '" + tenantB + "', false)");
            assertThat(count(s, "students")).isEqualTo(1);
            assertThat(count(s, "fees")).isZero();
        }
    }

    @Test
    void aTenantCannotWriteAnotherTenantsRow() throws Exception {
        try (Connection c = appConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.tenant_id', '" + tenantA + "', false)");
            assertThatThrownBy(() -> insertStudent(s, tenantB, "Intruder"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    void systemContextBypassSeesEveryTenant() throws Exception {
        try (Connection c = appConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.bypass_rls', 'on', false)");
            assertThat(count(s, "students")).isEqualTo(2);
        }
    }
}
