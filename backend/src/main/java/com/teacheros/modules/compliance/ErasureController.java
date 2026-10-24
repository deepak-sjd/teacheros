package com.teacheros.modules.compliance;

import com.teacheros.modules.compliance.dto.ComplianceDtos.DecideErasureRequest;
import com.teacheros.modules.compliance.dto.ComplianceDtos.ErasureRequestResponse;
import com.teacheros.modules.compliance.dto.ComplianceDtos.RequestErasureRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ErasureController {

    private final ErasureService erasureService;

    public ErasureController(ErasureService erasureService) {
        this.erasureService = erasureService;
    }

    // Guardian of that student, or staff - ErasureService enforces it (404
    // otherwise).
    @PostMapping("/students/{studentId}/erasure-requests")
    public ResponseEntity<ErasureRequestResponse> request(@PathVariable UUID studentId,
            @Valid @RequestBody RequestErasureRequest body) {
        return ResponseEntity.ok(erasureService.request(studentId, body.reason()));
    }

    @GetMapping("/students/{studentId}/erasure-requests")
    public ResponseEntity<List<ErasureRequestResponse>> forStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(erasureService.listForStudent(studentId));
    }

    @GetMapping("/erasure-requests")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Page<ErasureRequestResponse>> list(
            @RequestParam(required = false) ErasureStatus status,
            @PageableDefault(size = 20, sort = { "createdAt", "id" }) Pageable pageable) {
        return ResponseEntity.ok(erasureService.list(status, pageable));
    }

    @PostMapping("/erasure-requests/{id}/approve")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ErasureRequestResponse> approve(@PathVariable UUID id,
            @Valid @RequestBody(required = false) DecideErasureRequest body) {
        return ResponseEntity.ok(erasureService.approve(id, body == null ? null : body.note()));
    }

    @PostMapping("/erasure-requests/{id}/reject")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ErasureRequestResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody(required = false) DecideErasureRequest body) {
        return ResponseEntity.ok(erasureService.reject(id, body == null ? null : body.note()));
    }
}
