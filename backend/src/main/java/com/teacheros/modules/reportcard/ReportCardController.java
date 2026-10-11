package com.teacheros.modules.reportcard;

import com.teacheros.modules.reportcard.dto.ReportCardDtos.RemarkResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.SaveRemarksRequest;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.TermResultResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/report-cards")
public class ReportCardController {

    private final ReportCardService reportCardService;

    public ReportCardController(ReportCardService reportCardService) {
        this.reportCardService = reportCardService;
    }

    // Any role - ReportCardService enforces "your own child/self only" via
    // StudentAccessGuard, same pattern as GET /tests/students/{id}/marks.
    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<TermResultResponse>> studentResults(@PathVariable UUID studentId) {
        return ResponseEntity.ok(reportCardService.getStudentResults(studentId));
    }

    @GetMapping("/terms/{termId}/remarks")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<RemarkResponse>> listRemarks(@PathVariable UUID termId,
            @RequestParam UUID batchId) {
        return ResponseEntity.ok(reportCardService.listRemarks(termId, batchId));
    }

    @PutMapping("/terms/{termId}/remarks")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Void> saveRemarks(@PathVariable UUID termId,
            @Valid @RequestBody SaveRemarksRequest request) {
        reportCardService.saveRemarks(termId, request);
        return ResponseEntity.noContent().build();
    }
}
