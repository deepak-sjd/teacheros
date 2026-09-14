package com.teacheros.modules.fee;

import com.teacheros.modules.fee.dto.FeeDtos.CreateFeeRequest;
import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.fee.dto.FeeDtos.RecordPaymentRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<FeeResponse> create(@Valid @RequestBody CreateFeeRequest request) {
        return ResponseEntity.ok(feeService.createFee(request));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<FeeResponse> recordPayment(@PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.ok(feeService.recordPayment(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<FeeResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(feeService.get(id));
    }

    // Open to any authenticated role - FeeService enforces "your own child /
    // yourself only" for PARENT/STUDENT via StudentAccessGuard. This is the
    // endpoint the parent/student fee ledger views call.
    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<FeeResponse>> listForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(feeService.listForStudent(studentId));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Page<FeeResponse>> listPending(Pageable pageable) {
        return ResponseEntity.ok(feeService.listPending(pageable));
    }
}
