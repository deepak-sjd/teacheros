package com.teacheros.modules.assessment;

import com.teacheros.modules.assessment.dto.AttemptDtos.MyAttemptResponse;
import com.teacheros.modules.assessment.dto.AttemptDtos.StartTestResponse;
import com.teacheros.modules.assessment.dto.AttemptDtos.SubmitAnswersRequest;
import com.teacheros.modules.assessment.dto.AttemptDtos.SubmitResultResponse;
import com.teacheros.modules.assessment.dto.AttemptDtos.TestResultsResponse;
import com.teacheros.modules.assessment.dto.QuestionDtos.QuestionResponse;
import com.teacheros.modules.assessment.dto.QuestionDtos.ReplaceQuestionsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Sibling to AssessmentController (same /api/v1/tests base path) - kept
 * separate because everything here is specific to ONLINE_MCQ tests,
 * rather than bloating the type-agnostic controller with question/attempt
 * endpoints an OFFLINE test never uses.
 */
@RestController
@RequestMapping("/api/v1/tests")
public class OnlineTestController {

    private final OnlineTestService onlineTestService;

    public OnlineTestController(OnlineTestService onlineTestService) {
        this.onlineTestService = onlineTestService;
    }

    @PutMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<QuestionResponse>> replaceQuestions(@PathVariable UUID id,
            @Valid @RequestBody ReplaceQuestionsRequest request) {
        return ResponseEntity.ok(onlineTestService.replaceQuestions(id, request));
    }

    @GetMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<QuestionResponse>> getQuestions(@PathVariable UUID id) {
        return ResponseEntity.ok(onlineTestService.getQuestionsForStaff(id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StartTestResponse> start(@PathVariable UUID id) {
        return ResponseEntity.ok(onlineTestService.start(id));
    }

    @GetMapping("/{id}/my-attempt")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<MyAttemptResponse> myAttempt(@PathVariable UUID id) {
        return ResponseEntity.ok(onlineTestService.myAttempt(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmitResultResponse> submit(@PathVariable UUID id,
            @Valid @RequestBody SubmitAnswersRequest request) {
        return ResponseEntity.ok(onlineTestService.submit(id, request));
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<TestResultsResponse> results(@PathVariable UUID id) {
        return ResponseEntity.ok(onlineTestService.results(id));
    }
}
