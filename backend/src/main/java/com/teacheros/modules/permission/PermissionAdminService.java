package com.teacheros.modules.permission;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.PermissionGrantResponse;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.PermissionInfo;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.PermissionMatrixResponse;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.SetPermissionRequest;
import com.teacheros.modules.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads and edits this tenant's role -> permission matrix (role_permissions).
 *
 * Tenant always comes from the JWT. OWNER is locked: PermissionService
 * short-circuits OWNER to "allowed", so the matrix always shows OWNER holding
 * everything and refuses to edit it - an edited matrix can never lock the
 * owner out. PARENT/STUDENT are not part of the matrix at all.
 *
 * PermissionService reads role_permissions on every check, so a toggle takes
 * effect on the very next request - no cache to invalidate. (A role change
 * for a person, by contrast, lands at their next token refresh.)
 */
@Service
public class PermissionAdminService {

    /** Roles whose grants an owner may edit. */
    public static final List<Role> CONFIGURABLE_ROLES = List.of(
            Role.ADMIN, Role.TEACHER, Role.STAFF, Role.ACCOUNTANT, Role.COUNSELLOR);

    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AuditService auditService;

    public PermissionAdminService(RolePermissionRepository rolePermissionRepository,
            CurrentTenantProvider currentTenantProvider, AuditService auditService) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse getMatrix() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        Map<String, Set<String>> heldByRole = new HashMap<>();
        for (RolePermission rp : rolePermissionRepository.findByTenantId(tenantId)) {
            heldByRole.computeIfAbsent(rp.getRole(), r -> new HashSet<>()).add(rp.getPermission());
        }

        List<String> allNames = Arrays.stream(Permission.values()).map(Permission::name).toList();

        Map<String, List<String>> grants = new LinkedHashMap<>();
        grants.put(Role.OWNER.name(), allNames); // locked: always everything
        for (Role role : CONFIGURABLE_ROLES) {
            Set<String> held = heldByRole.getOrDefault(role.name(), Set.of());
            grants.put(role.name(), allNames.stream().filter(held::contains).toList());
        }

        List<String> roles = new java.util.ArrayList<>();
        roles.add(Role.OWNER.name());
        CONFIGURABLE_ROLES.forEach(r -> roles.add(r.name()));

        List<PermissionInfo> permissions = Arrays.stream(Permission.values())
                .map(p -> new PermissionInfo(p.name(), p.group(), p.description()))
                .toList();

        return new PermissionMatrixResponse(roles, List.of(Role.OWNER.name()), permissions, grants);
    }

    /**
     * Idempotent: asking for the state a role is already in changes nothing and
     * writes no audit row. A real change is audited in the same transaction.
     */
    @Transactional
    public PermissionGrantResponse setGrant(SetPermissionRequest request) {
        Role role = parseRole(request.role());
        if (role == Role.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_LOCKED",
                    "The owner always has every permission and cannot be edited.");
        }
        if (!CONFIGURABLE_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_CONFIGURABLE",
                    "Permissions can't be configured for " + role.name() + ".");
        }
        Permission permission = parsePermission(request.permission());
        boolean granted = Boolean.TRUE.equals(request.granted());

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        boolean before = rolePermissionRepository.existsByTenantIdAndRoleAndPermission(
                tenantId, role.name(), permission.name());

        if (before == granted) {
            return new PermissionGrantResponse(role.name(), permission.name(), granted);
        }

        if (granted) {
            rolePermissionRepository.grant(tenantId, role.name(), permission.name());
        } else {
            rolePermissionRepository.revoke(tenantId, role.name(), permission.name());
        }

        auditService.record(AuditActions.PERMISSION_CHANGED, AuditActions.ROLE_PERMISSION, null,
                AuditService.meta("role", role.name(), "permission", permission.name(),
                        "from", before, "to", granted));

        return new PermissionGrantResponse(role.name(), permission.name(), granted);
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "Unknown role: " + value);
        }
    }

    private Permission parsePermission(String value) {
        try {
            return Permission.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PERMISSION", "Unknown permission: " + value);
        }
    }
}
