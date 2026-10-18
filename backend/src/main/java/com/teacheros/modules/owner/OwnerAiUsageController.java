package com.teacheros.modules.owner;

import com.teacheros.modules.ai.AiUsageReportService;
import com.teacheros.modules.ai.dto.AiUsageDtos.AiUsageDashboardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OWNER only - not ADMIN/TEACHER/STAFF. Cost and failure figures are
 * business-level information; the tenant comes from the JWT inside the service.
 */
@RestController
@RequestMapping("/api/v1/owner/ai-usage")
@PreAuthorize("hasRole('OWNER')")
public class OwnerAiUsageController {

    private final AiUsageReportService reportService;

    public OwnerAiUsageController(AiUsageReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<AiUsageDashboardResponse> usage(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reportService.report(days));
    }
}
