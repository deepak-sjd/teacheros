package com.teacheros.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class TenantDtos {

    // logoUrl is a short-lived signed URL (same pattern as study material
    // downloads, see StorageService's javadoc) - null when no logo has
    // been uploaded, not an empty string.
    public record TenantSettingsResponse(
            UUID id,
            String name,
            String logoUrl) {
    }

    public record UpdateTenantRequest(
            @NotBlank String name) {
    }
}
