package com.teacheros.modules.homework;

import com.teacheros.modules.homework.dto.HomeworkSubmissionDtos.ReviewSubmissionRequest;
import com.teacheros.modules.homework.dto.HomeworkSubmissionDtos.SubmissionResponse;
import com.teacheros.modules.material.dto.StudyMaterialDtos.DownloadUrlResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// Sibling to HomeworkController (same base path) - kept separate so the
// assignment CRUD controller stays untouched.
@RestController
@RequestMapping("/api/v1/homework")
public class HomeworkSubmissionController {

    private final HomeworkSubmissionService submissionService;

    public HomeworkSubmissionController(HomeworkSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    // STUDENT only - submits as the caller's own linked student record.
    // POST (not PUT) because the frontend's uploadFile helper is POST-only;
    // the call is an idempotent upsert either way.
    @PostMapping(value = "/{homeworkId}/submission", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponse> submit(
            @PathVariable UUID homeworkId,
            @RequestParam(required = false) String text,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.ok(submissionService.submit(homeworkId, text, file));
    }

    // No @PreAuthorize - STUDENT (own), PARENT (own child) and staff all
    // reach this; StudentAccessGuard inside the service decides.
    @GetMapping("/{homeworkId}/students/{studentId}/submission")
    public ResponseEntity<SubmissionResponse> getForStudent(
            @PathVariable UUID homeworkId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(submissionService.getForStudent(homeworkId, studentId));
    }

    // Same: guarded per-student inside the service. Signed URL only.
    @GetMapping("/submissions/{submissionId}/file-url")
    public ResponseEntity<DownloadUrlResponse> getFileUrl(@PathVariable UUID submissionId) {
        return ResponseEntity.ok(submissionService.getFileUrl(submissionId));
    }

    @GetMapping("/{homeworkId}/submissions")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<SubmissionResponse>> listForHomework(@PathVariable UUID homeworkId) {
        return ResponseEntity.ok(submissionService.listForHomework(homeworkId));
    }

    @PatchMapping("/submissions/{submissionId}/review")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<SubmissionResponse> review(@PathVariable UUID submissionId,
            @Valid @RequestBody ReviewSubmissionRequest request) {
        return ResponseEntity.ok(submissionService.review(submissionId, request));
    }
}
