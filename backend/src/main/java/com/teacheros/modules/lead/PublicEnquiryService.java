package com.teacheros.modules.lead;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.course.Course;
import com.teacheros.modules.course.CourseRepository;
import com.teacheros.modules.lead.dto.LeadDtos.EnquiryRequest;
import com.teacheros.modules.tenant.PublicTenantResolver;
import com.teacheros.modules.tenant.Tenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Unauthenticated intake. The ONLY thing it can do is create a NEW lead:
 * it returns nothing and exposes no read or list path. The tenant is
 * resolved from the public slug (never from a tenant id supplied by the
 * client). If a courseId is sent it must be a PUBLISHED course of that same
 * tenant, so a draft course can't be probed through this endpoint either.
 */
@Service
public class PublicEnquiryService {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final int MESSAGE_MAX = 2000;

    private final PublicTenantResolver tenantResolver;
    private final CourseRepository courseRepository;
    private final LeadRepository leadRepository;
    private final LeadStatusHistoryRepository historyRepository;
    private final PublicEnquiryRateLimiter rateLimiter;
    private final Duration duplicateWindow;

    public PublicEnquiryService(
            PublicTenantResolver tenantResolver,
            CourseRepository courseRepository,
            LeadRepository leadRepository,
            LeadStatusHistoryRepository historyRepository,
            PublicEnquiryRateLimiter rateLimiter,
            @Value("${app.public.enquiry.duplicate-window-hours:24}") long duplicateWindowHours) {
        this.tenantResolver = tenantResolver;
        this.courseRepository = courseRepository;
        this.leadRepository = leadRepository;
        this.historyRepository = historyRepository;
        this.rateLimiter = rateLimiter;
        this.duplicateWindow = Duration.ofHours(duplicateWindowHours);
    }

    @Transactional
    public void submit(EnquiryRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire("ip:" + clientIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests. Please try again in a few minutes.");
        }

        // Honeypot: a bot filled the hidden field. Pretend success, store nothing.
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }

        Tenant tenant = tenantResolver.resolve(request.schoolSlug());

        String phone = request.phone().replaceAll("[\\s\\-()]", "");
        if (!PHONE.matcher(phone).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a valid phone number.");
        }

        Course course = null;
        if (request.courseId() != null) {
            course = courseRepository.findByIdAndTenantIdAndPublishedTrue(request.courseId(), tenant.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND",
                            "That course isn't available."));
        }

        // Public callers may only pick the two public sources; a course enquiry is
        // always COURSE_PAGE.
        LeadSource source = (course != null || request.source() == LeadSource.COURSE_PAGE)
                ? LeadSource.COURSE_PAGE
                : LeadSource.WEBSITE;

        // Same number already waiting as NEW recently: don't flood the pipeline.
        if (leadRepository.existsByTenantIdAndPhoneAndStatusAndCreatedAtAfter(
                tenant.getId(), phone, LeadStatus.NEW, Instant.now().minus(duplicateWindow))) {
            return;
        }

        String message = blankToNull(request.message());
        if (course != null) {
            String prefix = "Interested in course: " + course.getTitle();
            message = truncate(message == null ? prefix : prefix + "\n" + message, MESSAGE_MAX);
        }

        Lead lead = new Lead(tenant.getId(), request.name().trim(), phone, blankToNull(request.email()), message,
                source);
        lead = leadRepository.save(lead);
        historyRepository.save(new LeadStatusHistory(tenant.getId(), lead.getId(), null, LeadStatus.NEW,
                "Enquiry submitted", null));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
