package com.teacheros.modules.academic;

import com.teacheros.modules.academic.dto.PromotionDtos.PromotionPreviewResponse;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionRequest;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionResultResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Staff-only, SCHOOL tenants only. Preview never writes; apply is one
 * transaction.
 */
@RestController
@RequestMapping("/api/v1/academic/promotion")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping("/preview")
    @PreAuthorize("@perm.has('STUDENTS_VIEW')")
    public ResponseEntity<PromotionPreviewResponse> preview(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(promotionService.preview(request));
    }

    @PostMapping("/apply")
    @PreAuthorize("@perm.has('STUDENTS_EDIT')")
    public ResponseEntity<PromotionResultResponse> apply(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(promotionService.apply(request));
    }
}
