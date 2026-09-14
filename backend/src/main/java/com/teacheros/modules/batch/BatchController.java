package com.teacheros.modules.batch;

import com.teacheros.modules.batch.dto.BatchDtos.BatchResponse;
import com.teacheros.modules.batch.dto.BatchDtos.CreateBatchRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Staff-only module throughout - parents/students don't browse batches
// directly in this MVP, they see their child's data via the student detail
// endpoints instead. Revisit if a future screen needs batch info for
// parents (e.g. showing a class schedule).
@RestController
@RequestMapping("/api/v1/batches")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ResponseEntity<BatchResponse> create(@Valid @RequestBody CreateBatchRequest request) {
        return ResponseEntity.ok(batchService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<BatchResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(batchService.list(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.get(id));
    }
}
