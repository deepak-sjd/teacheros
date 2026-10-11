package com.teacheros.modules.course;

import com.teacheros.modules.course.dto.CourseContentDtos.EnrolledCourseSummary;
import com.teacheros.modules.course.dto.CourseContentDtos.LearnCourseResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.LessonDetailResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.PlaybackResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.ProgressResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.SaveProgressRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// STUDENT only - the student is resolved from the JWT inside the service.
// A course/lesson the student isn't enrolled in is a 404, never a 403.
@RestController
@RequestMapping("/api/v1/learn")
@PreAuthorize("hasRole('STUDENT')")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/courses")
    public ResponseEntity<List<EnrolledCourseSummary>> myCourses() {
        return ResponseEntity.ok(learningService.myCourses());
    }

    @GetMapping("/courses/{courseId}")
    public ResponseEntity<LearnCourseResponse> getCourse(@PathVariable UUID courseId) {
        return ResponseEntity.ok(learningService.getCourse(courseId));
    }

    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<LessonDetailResponse> getLesson(@PathVariable UUID lessonId) {
        return ResponseEntity.ok(learningService.getLesson(lessonId));
    }

    @GetMapping("/lessons/{lessonId}/playback")
    public ResponseEntity<PlaybackResponse> getPlayback(@PathVariable UUID lessonId) {
        return ResponseEntity.ok(learningService.getPlayback(lessonId));
    }

    @PutMapping("/lessons/{lessonId}/progress")
    public ResponseEntity<ProgressResponse> saveProgress(@PathVariable UUID lessonId,
            @Valid @RequestBody SaveProgressRequest request) {
        return ResponseEntity.ok(learningService.saveProgress(lessonId, request));
    }
}
