package com.teacheros.modules.permission;

import com.teacheros.modules.permission.dto.PermissionAdminDtos.PermissionGrantResponse;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.PermissionMatrixResponse;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.SetPermissionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Role-based on purpose (not @perm): managing permissions must not itself be
 * something the matrix can grant away or hand to someone else. OWNER and ADMIN
 * can read the matrix; only OWNER can change it.
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionAdminController {

    private final PermissionAdminService permissionAdminService;

    public PermissionAdminController(PermissionAdminService permissionAdminService) {
        this.permissionAdminService = permissionAdminService;
    }

    @GetMapping("/matrix")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<PermissionMatrixResponse> matrix() {
        return ResponseEntity.ok(permissionAdminService.getMatrix());
    }

    @PutMapping("/grants")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PermissionGrantResponse> setGrant(@Valid @RequestBody SetPermissionRequest request) {
        return ResponseEntity.ok(permissionAdminService.setGrant(request));
    }
}
