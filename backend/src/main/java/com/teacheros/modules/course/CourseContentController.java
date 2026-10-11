package com.teacheros.modules.course;

import com.teacheros.modules.course.dto.CourseContentDtos.SaveChapterRequest;
import com.teacheros.modules.course.dto.CourseContentDtos.SaveLessonRequest;
import com.teacheros.modules.course.dto.CourseContentDtos.StaffChapter;
import com.teacheros.modules.course.dto.CourseContentDtos.StaffLesson;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Staff authoring. Sibling to CourseController (which owns /courses CRUD and
// stays untouched). Staff-only for every method.
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class CourseContentController {

    private final CourseContentService contentService;

    public CourseContentController(CourseContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/courses/{courseId}/chapters")
    public ResponseEntity<List<StaffChapter>> list(@PathVariable UUID courseId) {
        return ResponseEntity.ok(contentService.list(courseId));
    }

    @PostMapping("/courses/{courseId}/chapters")
    public ResponseEntity<StaffChapter> createChapter(@PathVariable UUID courseId,
            @Valid @RequestBody SaveChapterRequest request) {
        return ResponseEntity.ok(contentService.createChapter(courseId, request));
    }

    @PutMapping("/chapters/{chapterId}")
    public ResponseEntity<StaffChapter> updateChapter(@PathVariable UUID chapterId,
            @Valid @RequestBody SaveChapterRequest request) {
        return ResponseEntity.ok(contentService.updateChapter(chapterId, request));
    }

    @DeleteMapping("/chapters/{chapterId}")
    public ResponseEntity<Void> deleteChapter(@PathVariable UUID chapterId) {
        contentService.deleteChapter(chapterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/chapters/{chapterId}/lessons")
    public ResponseEntity<StaffLesson> createLesson(@PathVariable UUID chapterId,
            @Valid @RequestBody SaveLessonRequest request) {
        return ResponseEntity.ok(contentService.createLesson(chapterId, request));
    }

    @PutMapping("/lessons/{lessonId}")
    public ResponseEntity<StaffLesson> updateLesson(@PathVariable UUID lessonId,
            @Valid @RequestBody SaveLessonRequest request) {
        return ResponseEntity.ok(contentService.updateLesson(lessonId, request));
    }

    @DeleteMapping("/lessons/{lessonId}")
    public ResponseEntity<Void> deleteLesson(@PathVariable UUID lessonId) {
        contentService.deleteLesson(lessonId);
        return ResponseEntity.noContent().build();
    }
}
