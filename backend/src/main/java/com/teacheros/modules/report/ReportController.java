package com.teacheros.modules.report;

import com.teacheros.modules.report.dto.ReportDtos.ReportSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportPdfService reportPdfService;

    public ReportController(ReportService reportService, ReportPdfService reportPdfService) {
        this.reportService = reportService;
        this.reportPdfService = reportPdfService;
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

    // Same access rule as /summary above, same params - just rendered as a
    // downloadable PDF instead of JSON. Generated on demand, same as the
    // JSON version; nothing is stored server-side.
    @GetMapping(value = "/summary.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<byte[]> summaryPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID batchId) {
        byte[] pdf = reportPdfService.generateSummaryPdf(from, to, batchId);
        return pdfResponse(pdf, "report-" + from + "-to-" + to + ".pdf");
    }

    // Open to any authenticated role, exactly like GET /fees/students/{id}
    // and GET /tests/students/{id}/marks - ReportPdfService enforces "your
    // own child/self only" internally via StudentAccessGuard before it
    // reads anything.
    @GetMapping(value = "/students/{studentId}/report-card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> studentReportCardPdf(@PathVariable UUID studentId) {
        byte[] pdf = reportPdfService.generateStudentReportCardPdf(studentId);
        return pdfResponse(pdf, "report-card-" + studentId + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
