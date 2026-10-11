package com.teacheros.modules.reportcard;

import com.teacheros.modules.reportcard.dto.ReportCardDtos.GradeScaleResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ReplaceGradeScaleRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/grade-scale")
public class GradeScaleController {

    private final GradeScaleService gradeScaleService;

    public GradeScaleController(GradeScaleService gradeScaleService) {
        this.gradeScaleService = gradeScaleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<GradeScaleResponse> get() {
        return ResponseEntity.ok(gradeScaleService.get());
    }

    // Changing grade boundaries changes every report card, so it's an
    // owner/admin decision - same as school settings (TenantController).
    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<GradeScaleResponse> replace(@Valid @RequestBody ReplaceGradeScaleRequest request) {
        return ResponseEntity.ok(gradeScaleService.replace(request));
    }

    // Returns the tenant to the CBSE-style defaults (returns the defaults, 200).
    @DeleteMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<GradeScaleResponse> reset() {
        return ResponseEntity.ok(gradeScaleService.reset());
    }
}
