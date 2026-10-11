package com.teacheros.modules.feestructure;

import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.DailyCollectionResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.DefaultersResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.HeadCollectionResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.OutstandingResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Staff-only (ACCOUNTANT included). Each report has a JSON endpoint and an .xlsx twin. */
@RestController
@RequestMapping("/api/v1/finance-reports")
@PreAuthorize(FeePermissions.VIEW)
public class FinanceReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FeeReportService reports;
    private final FinanceReportExporter exporter;

    public FinanceReportController(FeeReportService reports, FinanceReportExporter exporter) {
        this.reports = reports;
        this.exporter = exporter;
    }

    @GetMapping("/daily-collection")
    public ResponseEntity<DailyCollectionResponse> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reports.dailyCollection(date));
    }

    @GetMapping("/daily-collection/export.xlsx")
    public ResponseEntity<byte[]> dailyExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailyCollectionResponse r = reports.dailyCollection(date);
        return xlsx(exporter.dailyCollection(r), "daily-collection-" + r.date() + ".xlsx");
    }

    @GetMapping("/outstanding")
    public ResponseEntity<OutstandingResponse> outstanding(@RequestParam(defaultValue = "batch") String groupBy) {
        return ResponseEntity.ok(reports.outstanding(groupBy));
    }

    @GetMapping("/outstanding/export.xlsx")
    public ResponseEntity<byte[]> outstandingExport(@RequestParam(defaultValue = "batch") String groupBy) {
        OutstandingResponse r = reports.outstanding(groupBy);
        return xlsx(exporter.outstanding(r), "outstanding-by-" + r.groupBy() + "-" + r.asOf() + ".xlsx");
    }

    @GetMapping("/defaulters")
    public ResponseEntity<DefaultersResponse> defaulters(@RequestParam(defaultValue = "0") int minDaysOverdue) {
        return ResponseEntity.ok(reports.defaulters(minDaysOverdue));
    }

    @GetMapping("/defaulters/export.xlsx")
    public ResponseEntity<byte[]> defaultersExport(@RequestParam(defaultValue = "0") int minDaysOverdue) {
        DefaultersResponse r = reports.defaulters(minDaysOverdue);
        return xlsx(exporter.defaulters(r), "defaulters-" + r.asOf() + ".xlsx");
    }

    @GetMapping("/collection-by-head")
    public ResponseEntity<HeadCollectionResponse> byHead(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reports.collectionByHead(from, to));
    }

    @GetMapping("/collection-by-head/export.xlsx")
    public ResponseEntity<byte[]> byHeadExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        HeadCollectionResponse r = reports.collectionByHead(from, to);
        return xlsx(exporter.collectionByHead(r), "collection-by-head-" + r.from() + "-to-" + r.to() + ".xlsx");
    }

    private ResponseEntity<byte[]> xlsx(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
