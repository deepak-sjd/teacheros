package com.teacheros.modules.report;

import com.teacheros.modules.report.dto.ReportDtos.ReportSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // Staff-only, like every other management-facing endpoint in this app
    // (Alerts, Fees, Assessment grading) - reports summarize the whole
    // tenant/batch, not one student's own record, so PARENT/STUDENT access
    // doesn't apply here the way it does for e.g. /fees/students/{id}.
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<ReportSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID batchId) {
        return ResponseEntity.ok(reportService.generate(from, to, batchId));
    }
}
