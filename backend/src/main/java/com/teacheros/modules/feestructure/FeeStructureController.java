package com.teacheros.modules.feestructure;

import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignStructureRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentPreviewResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentResultResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.CreateFeeStructureRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.FeeStructureResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.UpdateFeeStructureRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fee-structures")
public class FeeStructureController {

    private final FeeStructureService structureService;
    private final FeeAssignmentService assignmentService;

    public FeeStructureController(FeeStructureService structureService, FeeAssignmentService assignmentService) {
        this.structureService = structureService;
        this.assignmentService = assignmentService;
    }

    @GetMapping
    @PreAuthorize(FeePermissions.VIEW)
    public ResponseEntity<List<FeeStructureResponse>> list() {
        return ResponseEntity.ok(structureService.list());
    }

    @PostMapping
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<FeeStructureResponse> create(@Valid @RequestBody CreateFeeStructureRequest request) {
        return ResponseEntity.ok(structureService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<FeeStructureResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateFeeStructureRequest request) {
        return ResponseEntity.ok(structureService.update(id, request));
    }

    @PostMapping("/preview")
    @PreAuthorize(FeePermissions.VIEW)
    public ResponseEntity<AssignmentPreviewResponse> preview(@Valid @RequestBody AssignStructureRequest request) {
        return ResponseEntity.ok(assignmentService.preview(request));
    }

    @PostMapping("/assign")
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<AssignmentResultResponse> assign(@Valid @RequestBody AssignStructureRequest request) {
        return ResponseEntity.ok(assignmentService.assign(request));
    }
}
