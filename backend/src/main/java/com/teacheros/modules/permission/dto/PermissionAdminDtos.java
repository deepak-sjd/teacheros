package com.teacheros.modules.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class PermissionAdminDtos {

    public record PermissionInfo(String name, String group, String description) {
    }

    /**
     * roles: column order for the matrix (OWNER first). lockedRoles: roles whose
     * grants cannot be edited (OWNER always has everything). grants: role name ->
     * permission names that role currently holds.
     */
    public record PermissionMatrixResponse(
            List<String> roles,
            List<String> lockedRoles,
            List<PermissionInfo> permissions,
            Map<String, List<String>> grants) {
    }

    public record SetPermissionRequest(
            @NotBlank String role,
            @NotBlank String permission,
            @NotNull Boolean granted) {
    }

    public record PermissionGrantResponse(String role, String permission, boolean granted) {
    }
}
