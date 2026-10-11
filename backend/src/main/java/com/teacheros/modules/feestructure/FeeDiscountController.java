package com.teacheros.modules.feestructure;

import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.CreateFeeDiscountRequest;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeDiscountResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.UpdateFeeDiscountRequest;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.WaiveLateFeeRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fee-discounts")
public class FeeDiscountController {

    private final FeeDiscountService service;

    public FeeDiscountController(FeeDiscountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(FeePermissions.VIEW)
    public ResponseEntity<List<FeeDiscountResponse>> list(@RequestParam(required = false) UUID studentId) {
        return ResponseEntity.ok(service.list(studentId));
    }

    @PostMapping
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<FeeDiscountResponse> create(@Valid @RequestBody CreateFeeDiscountRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<FeeDiscountResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateFeeDiscountRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/late-fee-waivers")
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<Void> waiveLateFee(@Valid @RequestBody WaiveLateFeeRequest request) {
        service.waiveLateFee(request);
        return ResponseEntity.noContent().build();
    }
}
