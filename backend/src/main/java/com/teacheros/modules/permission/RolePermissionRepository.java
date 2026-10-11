package com.teacheros.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    // Every finder takes tenantId, same rule as every other tenant-owned
    // repository.
    boolean existsByTenantIdAndRoleAndPermission(UUID tenantId, String role, String permission);

    /**
     * Copies role_permission_defaults into this tenant. Idempotent
     * (ON CONFLICT DO NOTHING against uq_role_permissions), so calling it twice
     * - or for a tenant V100 already seeded - changes nothing.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO role_permissions (tenant_id, role, permission)
            SELECT CAST(:tenantId AS uuid), d.role, d.permission
            FROM role_permission_defaults d
            ON CONFLICT DO NOTHING
            """)
    int seedDefaults(@Param("tenantId") UUID tenantId);
}
