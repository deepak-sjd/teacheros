package com.teacheros.modules.feestructure;

import com.teacheros.modules.feestructure.dto.FeeStructureDtos.CreateFeeHeadRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.FeeHeadResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.UpdateFeeHeadRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fee-heads")
public class FeeHeadController {

    private final FeeHeadService service;

    public FeeHeadController(FeeHeadService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(FeePermissions.VIEW)
    public ResponseEntity<List<FeeHeadResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<FeeHeadResponse> create(@Valid @RequestBody CreateFeeHeadRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<FeeHeadResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateFeeHeadRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }
}
