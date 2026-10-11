package com.teacheros.modules.tenant.dto;

import com.teacheros.modules.tenant.TenantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public class TenantDtos {

    // logoUrl is a short-lived signed URL (same pattern as study material
    // downloads, see StorageService's javadoc) - null when no logo has
    // been uploaded, not an empty string.
    public record TenantSettingsResponse(
            UUID id,
            String name,
            String slug,
            String logoUrl,
            TenantType tenantType,
            List<String> enabledModules) {
    }

    public record UpdateTenantRequest(
            @NotBlank String name) {
    }

    public record TenantConfigResponse(
            TenantType tenantType,
            List<String> enabledModules,
            String locale,
            String timezone,
            String primaryColor,
            String secondaryColor) {
    }

    // PATCH semantics: null = leave unchanged. For the two colours, "" clears
    // the value. tenantType is a String (validated in the service) so a bad
    // value returns a clean 400 INVALID_TENANT_TYPE.
    public record UpdateTenantConfigRequest(
            String tenantType,
            List<String> enabledModules,
            @Pattern(regexp = "^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$", message = "Enter a valid locale like en-IN") String locale,
            String timezone,
            @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "Use a #RRGGBB colour") String primaryColor,
            @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "Use a #RRGGBB colour") String secondaryColor) {
    }
}
