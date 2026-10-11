package com.teacheros.modules.permission;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registered as the bean "perm" so controllers can write
 * {@code @PreAuthorize("@perm.has('FEES_EDIT')")} instead of a long role list.
 *
 * Tenant and role always come from the authenticated principal
 * (CurrentTenantProvider), never from the request.
 *
 * Fails closed: no row for (tenant, role, permission) means denied. OWNER is
 * always allowed so an edited permission matrix can never lock the owner out.
 *
 * A misspelled permission name throws IllegalArgumentException (a 500, not a
 * silent allow). PreAuthorizePermissionNamesTest catches typos at build time.
 */
@Service("perm")
public class PermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public PermissionService(RolePermissionRepository rolePermissionRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    // Single String-arg method (no enum overload) so SpEL resolution is
    // unambiguous.
    public boolean has(String permissionName) {
        Permission permission = Permission.valueOf(permissionName);
        String role = currentTenantProvider.getCurrentRole();

        if (Role.OWNER.name().equals(role)) {
            return true;
        }
        return rolePermissionRepository.existsByTenantIdAndRoleAndPermission(
                currentTenantProvider.getCurrentTenantId(), role, permission.name());
    }

    /**
     * Called only from tenant provisioning with the ID of the tenant that was
     * just created in the same transaction - not a request-supplied tenant ID.
     */
    @Transactional
    public void seedDefaults(UUID tenantId) {
        rolePermissionRepository.seedDefaults(tenantId);
    }
}
