package com.teacheros.modules.feestructure;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeRulesRequest;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeRulesResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.LateFeeRunResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/fee-rules")
public class FeeRulesController {

    private final FeeRulesService rulesService;
    private final LateFeeService lateFeeService;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeRulesController(FeeRulesService rulesService, LateFeeService lateFeeService,
            CurrentTenantProvider currentTenantProvider) {
        this.rulesService = rulesService;
        this.lateFeeService = lateFeeService;
        this.currentTenantProvider = currentTenantProvider;
    }

    @GetMapping
    @PreAuthorize(FeePermissions.VIEW)
    public ResponseEntity<FeeRulesResponse> get() {
        return ResponseEntity.ok(rulesService.get());
    }

    @PutMapping
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<FeeRulesResponse> update(@Valid @RequestBody FeeRulesRequest request) {
        return ResponseEntity.ok(rulesService.update(request));
    }

    /** Runs the same job the nightly scheduler runs, for the caller's own tenant only. */
    @PostMapping("/late-fees/run")
    @PreAuthorize(FeePermissions.EDIT)
    public ResponseEntity<LateFeeRunResponse> runLateFees() {
        int created = lateFeeService.applyForTenant(currentTenantProvider.getCurrentTenantId(),
                LocalDate.now(FeeReportService.IST), currentTenantProvider.getCurrentUserId());
        return ResponseEntity.ok(new LateFeeRunResponse(created));
    }
}
