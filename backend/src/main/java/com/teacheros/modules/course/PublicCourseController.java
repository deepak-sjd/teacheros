package com.teacheros.modules.course;

import com.teacheros.modules.course.dto.CourseDtos.PublicCourseDetail;
import com.teacheros.modules.course.dto.CourseDtos.PublicCourseSummary;
import com.teacheros.modules.course.dto.CourseDtos.PublicSchoolResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * No @PreAuthorize - /api/v1/public/** is permitAll in SecurityConfig. GET
 * only, published data only. A short public cache keeps a popular course
 * page from hitting the database on every visit.
 */
@RestController
@RequestMapping("/api/v1/public/schools/{slug}")
public class PublicCourseController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final PublicCourseService publicCourseService;

    public PublicCourseController(PublicCourseService publicCourseService) {
        this.publicCourseService = publicCourseService;
    }

    @GetMapping
    public ResponseEntity<PublicSchoolResponse> school(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CACHE).body(publicCourseService.school(slug));
    }

    @GetMapping("/courses")
    public ResponseEntity<List<PublicCourseSummary>> list(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CACHE).body(publicCourseService.list(slug));
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<PublicCourseDetail> get(@PathVariable String slug, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CACHE).body(publicCourseService.get(slug, id));
    }
}
