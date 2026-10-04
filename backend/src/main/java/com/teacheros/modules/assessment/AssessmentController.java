package com.teacheros.modules.assessment;

import com.teacheros.modules.assessment.dto.AssessmentDtos.AssessmentResponse;
import com.teacheros.modules.assessment.dto.AssessmentDtos.CreateAssessmentRequest;
import com.teacheros.modules.assessment.dto.AssessmentDtos.UpdateAssessmentRequest;
import com.teacheros.modules.assessment.dto.MarkDtos.MarkResponse;
import com.teacheros.modules.assessment.dto.MarkDtos.StudentMarkHistoryEntry;
import com.teacheros.modules.assessment.dto.MarkDtos.SubmitMarksRequest;
import com.teacheros.modules.assessment.dto.RankingDtos.BatchRankingResponse;
import com.teacheros.modules.assessment.dto.RankingDtos.StudentRankResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tests")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<AssessmentResponse> create(@Valid @RequestBody CreateAssessmentRequest request) {
        return ResponseEntity.ok(assessmentService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<AssessmentResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateAssessmentRequest request) {
        return ResponseEntity.ok(assessmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assessmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    // Guarded inside the service via BatchAccessGuard, not a role check here.
    public ResponseEntity<AssessmentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(assessmentService.get(id));
    }

    @GetMapping
    public ResponseEntity<Page<AssessmentResponse>> listForBatch(
            @RequestParam UUID batchId,
            @RequestParam(required = false) TestType testType,
            Pageable pageable) {
        return ResponseEntity.ok(assessmentService.listForBatch(batchId, testType, pageable));
    }

    @PostMapping("/{id}/marks")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<MarkResponse>> submitMarks(@PathVariable UUID id,
            @Valid @RequestBody SubmitMarksRequest request) {
        return ResponseEntity.ok(assessmentService.submitMarks(id, request));
    }

    @GetMapping("/{id}/marks")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    // Staff only, not guarded-for-any-role like other reads - seeing every
    // student's marks for a test is a staff privilege, not something batch
    // membership alone should grant a parent/student.
    public ResponseEntity<List<MarkResponse>> listMarksForTest(@PathVariable UUID id) {
        return ResponseEntity.ok(assessmentService.listMarksForTest(id));
    }

    @GetMapping("/students/{studentId}/marks")
    // Any role, but StudentAccessGuard inside the service enforces
    // "only your own child" for PARENT/STUDENT - same pattern as
    // GET /fees/students/{studentId} and the attendance summary endpoint.
    public ResponseEntity<List<StudentMarkHistoryEntry>> getStudentMarkHistory(@PathVariable UUID studentId) {
        return ResponseEntity.ok(assessmentService.getStudentMarkHistory(studentId));
    }

    @GetMapping("/batches/{batchId}/ranking")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    // Staff only, same reasoning as listMarksForTest - a full-name
    // leaderboard is a staff privilege, not something batch membership
    // alone should grant a parent/student. See getStudentRank below for
    // the PARENT/STUDENT-safe equivalent.
    public ResponseEntity<BatchRankingResponse> batchRanking(@PathVariable UUID batchId) {
        return ResponseEntity.ok(assessmentService.getBatchRanking(batchId));
    }

    @GetMapping("/students/{studentId}/rank")
    // Any role, but StudentAccessGuard inside the service enforces "only
    // your own child/self" - same pattern as getStudentMarkHistory. Never
    // exposes a classmate's name or score, only this student's own
    // position and how many peers they're ranked against.
    public ResponseEntity<StudentRankResponse> studentRank(@PathVariable UUID studentId) {
        return ResponseEntity.ok(assessmentService.getStudentRank(studentId));
    }
}
