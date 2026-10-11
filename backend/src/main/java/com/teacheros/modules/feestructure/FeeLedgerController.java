package com.teacheros.modules.feestructure;

import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeLedgerLine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * No @PreAuthorize - open to any authenticated role, exactly like
 * GET /fees/students/{id}; FeeLedgerService enforces "your own child / self
 * only" via StudentAccessGuard before reading anything.
 */
@RestController
@RequestMapping("/api/v1/fee-ledger")
public class FeeLedgerController {

    private final FeeLedgerService service;

    public FeeLedgerController(FeeLedgerService service) {
        this.service = service;
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<FeeLedgerLine>> forStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(service.forStudent(studentId));
    }
}
