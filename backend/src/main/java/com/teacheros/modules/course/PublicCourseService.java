package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.modules.course.dto.CourseDtos.PublicCourseDetail;
import com.teacheros.modules.course.dto.CourseDtos.PublicCourseSummary;
import com.teacheros.modules.course.dto.CourseDtos.PublicSchoolResponse;
import com.teacheros.modules.tenant.PublicTenantResolver;
import com.teacheros.modules.tenant.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, unauthenticated catalogue. The tenant comes from the public
 * slug (PublicTenantResolver); every course lookup is tenant-scoped AND
 * published-only, so a draft is indistinguishable from a course that
 * doesn't exist. Nothing here can write.
 */
@Service
public class PublicCourseService {

    private static final int SHORT_DESCRIPTION_MAX = 160;

    private final PublicTenantResolver tenantResolver;
    private final CourseRepository courseRepository;
    private final StorageService storageService;

    public PublicCourseService(PublicTenantResolver tenantResolver, CourseRepository courseRepository,
            StorageService storageService) {
        this.tenantResolver = tenantResolver;
        this.courseRepository = courseRepository;
        this.storageService = storageService;
    }

    public PublicSchoolResponse school(String slug) {
        Tenant tenant = tenantResolver.resolve(slug);
        String logoUrl = tenant.getLogoObjectKey() == null
                ? null
                : storageService.generateDownloadUrl(tenant.getLogoObjectKey());
        return new PublicSchoolResponse(tenant.getName(), logoUrl);
    }

    public List<PublicCourseSummary> list(String slug) {
        Tenant tenant = tenantResolver.resolve(slug);
        return courseRepository.findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(tenant.getId()).stream()
                .map(c -> new PublicCourseSummary(c.getId(), c.getTitle(), shorten(c.getDescription()), c.getPrice(),
                        c.getDemoVideoUrl() != null))
                .toList();
    }

    public PublicCourseDetail get(String slug, UUID courseId) {
        Tenant tenant = tenantResolver.resolve(slug);
        Course c = courseRepository.findByIdAndTenantIdAndPublishedTrue(courseId, tenant.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND",
                        "That course isn't available."));
        return new PublicCourseDetail(c.getId(), c.getTitle(), c.getDescription(), c.getPrice(), c.getSyllabus(),
                c.getDemoVideoUrl());
    }

    private String shorten(String description) {
        if (description == null || description.isBlank())
            return null;
        String flat = description.trim().replaceAll("\\s+", " ");
        return flat.length() <= SHORT_DESCRIPTION_MAX ? flat
                : flat.substring(0, SHORT_DESCRIPTION_MAX - 1).trim() + "\u2026";
    }
}
