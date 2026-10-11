package com.teacheros.modules.permission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * role and permission are plain strings (not enums) on purpose: an unknown
 * value in the table must never make entity loading throw for a whole tenant.
 * PermissionService parses against the Permission enum at check time.
 */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false, length = 50)
    private String permission;

    public RolePermission(UUID tenantId, String role, String permission) {
        this.tenantId = tenantId;
        this.role = role;
        this.permission = permission;
    }
}
