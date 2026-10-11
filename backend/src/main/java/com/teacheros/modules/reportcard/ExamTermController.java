package com.teacheros.modules.reportcard;

import com.teacheros.modules.reportcard.dto.ReportCardDtos.ExamTermRequest;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ExamTermResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Staff-only: parents/students see terms only through their own report card results.
@RestController
@RequestMapping("/api/v1/exam-terms")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class ExamTermController {

    private final ExamTermService examTermService;

    public ExamTermController(ExamTermService examTermService) {
        this.examTermService = examTermService;
    }

    @GetMapping
    public ResponseEntity<List<ExamTermResponse>> list() {
        return ResponseEntity.ok(examTermService.list());
    }

    @PostMapping
    public ResponseEntity<ExamTermResponse> create(@Valid @RequestBody ExamTermRequest request) {
        return ResponseEntity.ok(examTermService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ExamTermResponse> update(@PathVariable UUID id,
            @Valid @RequestBody ExamTermRequest request) {
        return ResponseEntity.ok(examTermService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        examTermService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
