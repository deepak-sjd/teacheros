package com.teacheros.modules.studyplan;

import com.teacheros.modules.studyplan.dto.StudyPlanDtos.StudyPlanResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * No @PreAuthorize - same pattern as DoubtController/MessageController:
 * access is decided inside StudyPlanService via StudentAccessGuard, not
 * at the controller layer.
 */
@RestController
@RequestMapping("/api/v1/study-plan")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<StudyPlanResponse> get(@PathVariable UUID studentId) {
        return ResponseEntity.ok(studyPlanService.get(studentId));
    }

    @PostMapping("/students/{studentId}/regenerate")
    public ResponseEntity<StudyPlanResponse> regenerate(@PathVariable UUID studentId) {
        return ResponseEntity.ok(studyPlanService.regenerate(studentId));
    }
}
