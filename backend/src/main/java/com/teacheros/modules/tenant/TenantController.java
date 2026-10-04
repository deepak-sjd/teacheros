package com.teacheros.modules.tenant;

import com.teacheros.modules.tenant.dto.TenantDtos.TenantSettingsResponse;
import com.teacheros.modules.tenant.dto.TenantDtos.UpdateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    // No @PreAuthorize - every role can see the school's own name/logo
    // (e.g. to show it in the sidebar), same reasoning as UserController's
    // own-profile endpoints being open to all roles.
    @GetMapping
    public ResponseEntity<TenantSettingsResponse> getSettings() {
        return ResponseEntity.ok(tenantService.getSettings());
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<TenantSettingsResponse> updateSettings(@Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.updateSettings(request));
    }

    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<TenantSettingsResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(tenantService.uploadLogo(file));
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<TenantSettingsResponse> deleteLogo() {
        return ResponseEntity.ok(tenantService.deleteLogo());
    }
}
