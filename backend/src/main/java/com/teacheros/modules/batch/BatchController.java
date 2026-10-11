package com.teacheros.modules.batch;

import com.teacheros.modules.batch.dto.BatchDtos.BatchResponse;
import com.teacheros.modules.batch.dto.BatchDtos.CreateBatchRequest;
import com.teacheros.modules.batch.dto.BatchDtos.UpdateBatchRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Staff-only module throughout - parents/students don't browse batches
// directly in this MVP, they see their child's data via the student detail
// endpoints instead. The class-level role list still applies to every method
// that doesn't declare its own @PreAuthorize (create, get, update,
// deactivate); list is permission-gated (BATCHES_VIEW) so the Reports page
// can offer a batch filter to an accountant.
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
    @PreAuthorize("@perm.has('BATCHES_VIEW')")
    public ResponseEntity<Page<BatchResponse>> list(
            @PageableDefault(size = 20, sort = { "name", "id" }) Pageable pageable) {
        return ResponseEntity.ok(batchService.list(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BatchResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateBatchRequest request) {
        return ResponseEntity.ok(batchService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        batchService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
