package com.teacheros.common.security.rls;

import com.teacheros.common.security.AuthenticatedPrincipal;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Stamps every connection taken from the pool with the current request's tenant
 * (from the JWT) BEFORE any query runs. Values are session-level and are
 * overwritten on every acquisition, so a pooled connection never carries a
 * previous request's tenant. No tenant and no system context => empty =>
 * RLS returns zero rows.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return scope(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return scope(super.getConnection(username, password));
    }

    private Connection scope(Connection connection) throws SQLException {
        String tenant = "";
        String bypass = "off";
        if (SystemContext.isActive()) {
            bypass = "on";
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal p) {
                tenant = p.tenantId().toString();
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false), set_config('app.bypass_rls', ?, false)")) {
            ps.setString(1, tenant);
            ps.setString(2, bypass);
            ps.execute();
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // original failure is the one that matters
            }
            throw e;
        }
        return connection;
    }
}
