package com.teacheros.modules.course;

import com.teacheros.modules.course.dto.CourseDtos.CourseResponse;
import com.teacheros.modules.course.dto.CourseDtos.SaveCourseRequest;
import com.teacheros.modules.course.dto.CourseDtos.SetPublishedRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<List<CourseResponse>> list() {
        return ResponseEntity.ok(courseService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.get(id));
    }

    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody SaveCourseRequest request) {
        return ResponseEntity.ok(courseService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> update(@PathVariable UUID id, @Valid @RequestBody SaveCourseRequest request) {
        return ResponseEntity.ok(courseService.update(id, request));
    }

    @PatchMapping("/{id}/published")
    public ResponseEntity<CourseResponse> setPublished(@PathVariable UUID id,
            @Valid @RequestBody SetPublishedRequest request) {
        return ResponseEntity.ok(courseService.setPublished(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
