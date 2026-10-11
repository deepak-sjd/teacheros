package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.course.dto.CourseDtos.CourseResponse;
import com.teacheros.modules.course.dto.CourseDtos.SaveCourseRequest;
import com.teacheros.modules.course.dto.CourseDtos.SetPublishedRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Staff-side course CRUD. Public reads live in PublicCourseService. */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final BatchRepository batchRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public CourseService(CourseRepository courseRepository, BatchRepository batchRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.courseRepository = courseRepository;
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<CourseResponse> list() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        List<Course> courses = courseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<UUID, String> names = batchNames(tenantId, courses);
        return courses.stream().map(c -> toResponse(c, names)).toList();
    }

    public CourseResponse get(UUID courseId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return toResponse(require(tenantId, courseId), tenantId);
    }

    @Transactional
    public CourseResponse create(SaveCourseRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Course course = new Course(tenantId, request.title().trim(), request.price());
        apply(course, request, tenantId);
        return toResponse(courseRepository.save(course), tenantId);
    }

    @Transactional
    public CourseResponse update(UUID courseId, SaveCourseRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Course course = require(tenantId, courseId);
        course.setTitle(request.title().trim());
        course.setPrice(request.price());
        apply(course, request, tenantId);
        return toResponse(courseRepository.save(course), tenantId);
    }

    @Transactional
    public CourseResponse setPublished(UUID courseId, SetPublishedRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Course course = require(tenantId, courseId);
        course.setPublished(request.published());
        return toResponse(courseRepository.save(course), tenantId);
    }

    @Transactional
    public void delete(UUID courseId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        courseRepository.delete(require(tenantId, courseId));
    }

    // ---- helpers ----

    private Course require(UUID tenantId, UUID courseId) {
        return courseRepository.findByIdAndTenantId(courseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private void apply(Course course, SaveCourseRequest request, UUID tenantId) {
        if (request.batchId() != null) {
            // Tenant-isolation check: the batch must belong to this tenant.
            batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));
        }
        course.setBatchId(request.batchId());
        course.setDescription(blankToNull(request.description()));
        course.setSyllabus(blankToNull(request.syllabus()));
        course.setDemoVideoUrl(validVideoUrl(request.demoVideoUrl()));
        course.setPublished(request.published());
    }

    private String validVideoUrl(String url) {
        String trimmed = blankToNull(url);
        if (trimmed == null)
            return null;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_URL",
                    "Demo video link must be a full URL starting with http:// or https://");
        }
        return trimmed;
    }

    private Map<UUID, String> batchNames(UUID tenantId, List<Course> courses) {
        Set<UUID> ids = courses.stream().map(Course::getBatchId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty())
            return Map.of();
        return batchRepository.findAllById(ids).stream()
                .filter(b -> tenantId.equals(b.getTenantId()))
                .collect(Collectors.toMap(Batch::getId, Batch::getName, (a, b) -> a));
    }

    private CourseResponse toResponse(Course course, UUID tenantId) {
        return toResponse(course, batchNames(tenantId, List.of(course)));
    }

    private CourseResponse toResponse(Course c, Map<UUID, String> names) {
        return new CourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getPrice(), c.getSyllabus(),
                c.getDemoVideoUrl(), c.getBatchId(), c.getBatchId() == null ? null : names.get(c.getBatchId()),
                c.isPublished(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
