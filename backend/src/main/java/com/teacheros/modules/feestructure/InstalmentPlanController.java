package com.teacheros.modules.feestructure;

import com.teacheros.modules.feestructure.dto.FeeStructureDtos.CreateInstalmentPlanRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.InstalmentPlanResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instalment-plans")
public class InstalmentPlanController {

    private final InstalmentPlanService service;

    public InstalmentPlanController(InstalmentPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(FeePermissions.VIEW)
    public ResponseEntity<List<InstalmentPlanResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<InstalmentPlanResponse> create(@Valid @RequestBody CreateInstalmentPlanRequest request) {
        return ResponseEntity.ok(service.create(request));
    }
}
