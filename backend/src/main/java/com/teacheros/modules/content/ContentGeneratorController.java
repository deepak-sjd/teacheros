package com.teacheros.modules.content;

import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateNotesRequest;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateNotesResponse;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateQuestionsRequest;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateQuestionsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher-facing AI drafting tools. Staff-only, same role set as the
 * Tests/Questions and Study Material endpoints these drafts feed into
 * (OWNER/TEACHER/STAFF/ADMIN) - nothing here is ever reachable by
 * STUDENT/PARENT.
 */
@RestController
@RequestMapping("/api/v1/content-generator")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class ContentGeneratorController {

    private final ContentGeneratorService contentGeneratorService;

    public ContentGeneratorController(ContentGeneratorService contentGeneratorService) {
        this.contentGeneratorService = contentGeneratorService;
    }

    @PostMapping("/questions")
    public ResponseEntity<GenerateQuestionsResponse> generateQuestions(
            @Valid @RequestBody GenerateQuestionsRequest request) {
        return ResponseEntity.ok(contentGeneratorService.generateQuestions(request));
    }

    @PostMapping("/notes")
    public ResponseEntity<GenerateNotesResponse> generateNotes(
            @Valid @RequestBody GenerateNotesRequest request) {
        return ResponseEntity.ok(contentGeneratorService.generateNotes(request));
    }
}
