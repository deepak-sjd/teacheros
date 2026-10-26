package com.teacheros.modules.demo;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.course.Course;
import com.teacheros.modules.course.CourseRepository;
import com.teacheros.modules.demo.dto.DemoDtos.PublicDemoBookingRequest;
import com.teacheros.modules.demo.dto.DemoDtos.PublicDemoBookingResponse;
import com.teacheros.modules.demo.dto.DemoDtos.PublicDemoSlot;
import com.teacheros.modules.lead.Lead;
import com.teacheros.modules.lead.LeadRepository;
import com.teacheros.modules.lead.LeadSource;
import com.teacheros.modules.lead.LeadStatus;
import com.teacheros.modules.lead.LeadStatusHistory;
import com.teacheros.modules.lead.LeadStatusHistoryRepository;
import com.teacheros.modules.lead.PublicEnquiryRateLimiter;
import com.teacheros.modules.tenant.PublicTenantResolver;
import com.teacheros.modules.tenant.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Unauthenticated demo booking. Tenant comes from the public slug only. It can
 * list upcoming slots
 * and create a booking (plus a lead if the phone is new); it exposes no booking
 * or lead data.
 */
@Service
public class PublicDemoService {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final int MAX_PUBLIC_SLOTS = 50;

    private final PublicTenantResolver tenantResolver;
    private final DemoSlotRepository slotRepository;
    private final DemoBookingRepository bookingRepository;
    private final LeadRepository leadRepository;
    private final LeadStatusHistoryRepository historyRepository;
    private final CourseRepository courseRepository;
    private final DemoBookingCore core;
    private final PublicEnquiryRateLimiter rateLimiter;

    public PublicDemoService(PublicTenantResolver tenantResolver, DemoSlotRepository slotRepository,
            DemoBookingRepository bookingRepository, LeadRepository leadRepository,
            LeadStatusHistoryRepository historyRepository, CourseRepository courseRepository,
            DemoBookingCore core, PublicEnquiryRateLimiter rateLimiter) {
        this.tenantResolver = tenantResolver;
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.leadRepository = leadRepository;
        this.historyRepository = historyRepository;
        this.courseRepository = courseRepository;
        this.core = core;
        this.rateLimiter = rateLimiter;
    }

    /**
     * courseId narrows to that course's slots plus general (course-less,
     * batch-less) slots.
     */
    public List<PublicDemoSlot> slots(String slug, UUID courseId) {
        Tenant tenant = tenantResolver.resolve(slug);
        List<DemoSlot> slots = slotRepository
                .findByTenantIdAndCancelledFalseAndStartsAtGreaterThanOrderByStartsAtAsc(tenant.getId(), Instant.now())
                .stream()
                .filter(s -> courseId == null || courseId.equals(s.getCourseId())
                        || (s.getCourseId() == null && s.getBatchId() == null))
                .limit(MAX_PUBLIC_SLOTS).toList();
        if (slots.isEmpty())
            return List.of();

        Map<UUID, Long> counts = bookingRepository
                .countActiveBySlot(tenant.getId(), slots.stream().map(DemoSlot::getId).toList()).stream()
                .collect(Collectors.toMap(DemoBookingRepository.SlotCount::getSlotId,
                        DemoBookingRepository.SlotCount::getTotal));
        Set<UUID> courseIds = slots.stream().map(DemoSlot::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // Only published course titles are ever shown publicly.
        Map<UUID, String> titles = courseIds.isEmpty() ? Map.of()
                : courseRepository.findAllById(courseIds).stream()
                        .filter(c -> tenant.getId().equals(c.getTenantId()) && c.isPublished())
                        .collect(Collectors.toMap(Course::getId, Course::getTitle));
        return slots.stream().map(s -> new PublicDemoSlot(s.getId(), s.getStartsAt(), s.getDurationMinutes(),
                s.getLocation(), Math.max(s.getCapacity() - counts.getOrDefault(s.getId(), 0L), 0),
                s.getCourseId() == null ? null : titles.get(s.getCourseId()))).toList();
    }

    /** Returns null for a honeypot hit (pretend success, store nothing). */
    @Transactional
    public PublicDemoBookingResponse book(PublicDemoBookingRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire("demo-ip:" + clientIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests. Please try again in a few minutes.");
        }
        if (request.website() != null && !request.website().isBlank()) {
            return null;
        }

        Tenant tenant = tenantResolver.resolve(request.schoolSlug());
        String phone = request.phone().replaceAll("[\\s\\-()]", "");
        if (!PHONE.matcher(phone).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a valid phone number.");
        }

        // Read the slot first (unlocked) to validate it and pick the lead's source;
        // core.book re-checks under lock.
        DemoSlot peek = slotRepository.findByIdAndTenantId(request.slotId(), tenant.getId())
                .filter(s -> !s.isCancelled())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND",
                        "That demo slot isn't available."));

        Lead lead = leadRepository.findFirstByTenantIdAndPhoneOrderByCreatedAtDesc(tenant.getId(), phone).orElse(null);
        String email = blankToNull(request.email());
        if (lead == null) {
            lead = leadRepository.save(new Lead(tenant.getId(), request.name().trim(), phone, email,
                    "Booked a demo class", peek.getCourseId() != null ? LeadSource.COURSE_PAGE : LeadSource.WEBSITE));
            historyRepository.save(new LeadStatusHistory(tenant.getId(), lead.getId(), null, LeadStatus.NEW,
                    "Demo booking", null));
        } else if (lead.getEmail() == null && email != null) {
            lead.setEmail(email);
            lead = leadRepository.save(lead);
        }

        // A full or just-taken slot throws here, rolling back any lead created above.
        DemoBookingCore.Booked booked = core.book(tenant.getId(), lead, request.slotId(), null);
        return new PublicDemoBookingResponse(booked.slot().getStartsAt(), booked.slot().getDurationMinutes(),
                booked.slot().getLocation());
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
