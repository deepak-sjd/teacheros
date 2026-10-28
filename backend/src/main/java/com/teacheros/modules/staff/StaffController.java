package com.teacheros.modules.staff;

import com.teacheros.modules.staff.dto.StaffDtos.ChangeStaffRoleRequest;
import com.teacheros.modules.staff.dto.StaffDtos.CreateStaffRequest;
import com.teacheros.modules.staff.dto.StaffDtos.StaffResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Role-based on purpose (not @perm): staff management must not be something
 * the permission matrix can grant to, say, an accountant. StaffService adds
 * the finer rules (only an owner touches owners, last owner, self).
 */
@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ResponseEntity<List<StaffResponse>> list() {
        return ResponseEntity.ok(staffService.list());
    }

    @PostMapping
    public ResponseEntity<StaffResponse> create(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.ok(staffService.create(request));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<StaffResponse> changeRole(@PathVariable UUID id,
            @Valid @RequestBody ChangeStaffRoleRequest request) {
        return ResponseEntity.ok(staffService.changeRole(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<StaffResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.deactivate(id));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<StaffResponse> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.reactivate(id));
    }
}
