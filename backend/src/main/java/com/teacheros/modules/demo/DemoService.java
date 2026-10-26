package com.teacheros.modules.demo;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.course.Course;
import com.teacheros.modules.course.CourseRepository;
import com.teacheros.modules.demo.dto.DemoDtos.BookLeadRequest;
import com.teacheros.modules.demo.dto.DemoDtos.BookingResponse;
import com.teacheros.modules.demo.dto.DemoDtos.CreateSlotRequest;
import com.teacheros.modules.demo.dto.DemoDtos.MarkAttendanceRequest;
import com.teacheros.modules.demo.dto.DemoDtos.SlotResponse;
import com.teacheros.modules.lead.Lead;
import com.teacheros.modules.lead.LeadRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff-side demo slots and bookings. Public booking is PublicDemoService; both
 * share DemoBookingCore.
 */
@Service
public class DemoService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private final DemoSlotRepository slotRepository;
    private final DemoBookingRepository bookingRepository;
    private final LeadRepository leadRepository;
    private final CourseRepository courseRepository;
    private final BatchRepository batchRepository;
    private final DemoBookingCore core;
    private final CurrentTenantProvider currentTenantProvider;

    public DemoService(DemoSlotRepository slotRepository, DemoBookingRepository bookingRepository,
            LeadRepository leadRepository, CourseRepository courseRepository, BatchRepository batchRepository,
            DemoBookingCore core, CurrentTenantProvider currentTenantProvider) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.leadRepository = leadRepository;
        this.courseRepository = courseRepository;
        this.batchRepository = batchRepository;
        this.core = core;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        if (request.courseId() != null && request.batchId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_TARGET", "Pick a course or a batch, not both.");
        }
        if (request.courseId() != null) {
            courseRepository.findByIdAndTenantId(request.courseId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", request.courseId()));
        }
        if (request.batchId() != null) {
            batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));
        }
        DemoSlot slot = new DemoSlot(tenantId, request.startsAt(), request.durationMinutes(), request.capacity());
        slot.setCourseId(request.courseId());
        slot.setBatchId(request.batchId());
        slot.setLocation(blankToNull(request.location()));
        return toSlotResponses(tenantId, List.of(slotRepository.save(slot))).get(0);
    }

    /** Defaults to today .. +30 days. */
    public List<SlotResponse> listSlots(LocalDate from, LocalDate to) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        LocalDate start = from != null ? from : LocalDate.now(APP_ZONE);
        LocalDate end = to != null ? to : start.plusDays(30);
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "\"from\" must not be after \"to\"");
        }
        List<DemoSlot> slots = slotRepository.findByTenantIdAndStartsAtBetweenOrderByStartsAtAsc(tenantId,
                start.atStartOfDay(APP_ZONE).toInstant(), end.plusDays(1).atStartOfDay(APP_ZONE).toInstant());
        return toSlotResponses(tenantId, slots);
    }

    public List<BookingResponse> bookingsForSlot(UUID slotId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        DemoSlot slot = requireSlot(tenantId, slotId);
        return toBookingResponses(tenantId,
                bookingRepository.findByTenantIdAndSlotIdOrderByCreatedAtAsc(tenantId, slotId),
                Map.of(slot.getId(), slot));
    }

    /** Cancels the slot and every booked attendee on it (each is texted). */
    @Transactional
    public SlotResponse cancelSlot(UUID slotId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        DemoSlot slot = slotRepository.lockByIdAndTenantId(slotId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Demo slot", slotId));
        if (!slot.isCancelled()) {
            slot.setCancelled(true);
            slotRepository.save(slot);
            UUID userId = currentTenantProvider.getCurrentUserId();
            for (DemoBooking b : bookingRepository.findByTenantIdAndSlotIdOrderByCreatedAtAsc(tenantId, slotId)) {
                if (b.getStatus() == DemoBookingStatus.BOOKED) {
                    core.cancelBooking(b, slot, userId, true);
                }
            }
        }
        return toSlotResponses(tenantId, List.of(slot)).get(0);
    }

    @Transactional
    public BookingResponse bookLead(UUID leadId, BookLeadRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
        DemoBookingCore.Booked booked = core.book(tenantId, lead, request.slotId(),
                currentTenantProvider.getCurrentUserId());
        return toBookingResponses(tenantId, List.of(booked.booking()), Map.of(booked.slot().getId(), booked.slot()))
                .get(0);
    }

    public List<BookingResponse> bookingsForLead(UUID leadId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
        return toBookingResponses(tenantId,
                bookingRepository.findByTenantIdAndLeadIdOrderByCreatedAtDesc(tenantId, leadId),
                null);
    }

    @Transactional
    public BookingResponse markAttendance(UUID bookingId, MarkAttendanceRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        if (request.status() != DemoBookingStatus.SHOWED && request.status() != DemoBookingStatus.NO_SHOW) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Mark a booking as SHOWED or NO_SHOW.");
        }
        DemoBooking booking = requireBooking(tenantId, bookingId);
        if (booking.getStatus() == DemoBookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_CANCELLED", "This booking was cancelled.");
        }
        DemoSlot slot = requireSlot(tenantId, booking.getSlotId());
        if (slot.getStartsAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_EARLY",
                    "Attendance can be marked once the demo has started.");
        }
        core.applyAttendance(booking, request.status(), currentTenantProvider.getCurrentUserId());
        return toBookingResponses(tenantId, List.of(booking), Map.of(slot.getId(), slot)).get(0);
    }

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        DemoBooking booking = requireBooking(tenantId, bookingId);
        if (booking.getStatus() != DemoBookingStatus.BOOKED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CANCELLABLE",
                    "Only a booking that hasn't been marked yet can be cancelled.");
        }
        DemoSlot slot = requireSlot(tenantId, booking.getSlotId());
        core.cancelBooking(booking, slot, currentTenantProvider.getCurrentUserId(), false);
        return toBookingResponses(tenantId, List.of(booking), Map.of(slot.getId(), slot)).get(0);
    }

    // ---- helpers ----

    private DemoSlot requireSlot(UUID tenantId, UUID slotId) {
        return slotRepository.findByIdAndTenantId(slotId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Demo slot", slotId));
    }

    private DemoBooking requireBooking(UUID tenantId, UUID bookingId) {
        return bookingRepository.findByIdAndTenantId(bookingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Demo booking", bookingId));
    }

    private List<SlotResponse> toSlotResponses(UUID tenantId, List<DemoSlot> slots) {
        if (slots.isEmpty())
            return List.of();
        Map<UUID, Long> counts = bookingRepository
                .countActiveBySlot(tenantId, slots.stream().map(DemoSlot::getId).toList()).stream()
                .collect(Collectors.toMap(DemoBookingRepository.SlotCount::getSlotId,
                        DemoBookingRepository.SlotCount::getTotal));
        Set<UUID> courseIds = slots.stream().map(DemoSlot::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> titles = courseIds.isEmpty() ? Map.of()
                : courseRepository.findAllById(courseIds).stream().filter(c -> tenantId.equals(c.getTenantId()))
                        .collect(Collectors.toMap(Course::getId, Course::getTitle));
        return slots.stream().map(s -> {
            long booked = counts.getOrDefault(s.getId(), 0L);
            return new SlotResponse(s.getId(), s.getCourseId(),
                    s.getCourseId() == null ? null : titles.get(s.getCourseId()),
                    s.getBatchId(), s.getStartsAt(), s.getDurationMinutes(), s.getCapacity(), booked,
                    Math.max(s.getCapacity() - booked, 0), s.getLocation(), s.isCancelled());
        }).toList();
    }

    /** knownSlots may be null, in which case slot times are loaded here. */
    private List<BookingResponse> toBookingResponses(UUID tenantId, List<DemoBooking> list,
            Map<UUID, DemoSlot> knownSlots) {
        Map<UUID, DemoSlot> slots = new HashMap<>(knownSlots == null ? Map.of() : knownSlots);
        Set<UUID> missing = list.stream().map(DemoBooking::getSlotId).filter(id -> !slots.containsKey(id))
                .collect(Collectors.toSet());
        slotRepository.findAllById(missing).stream().filter(s -> tenantId.equals(s.getTenantId()))
                .forEach(s -> slots.put(s.getId(), s));
        Map<UUID, Lead> leads = leadRepository
                .findAllById(list.stream().map(DemoBooking::getLeadId).collect(Collectors.toSet())).stream()
                .filter(l -> tenantId.equals(l.getTenantId())).collect(Collectors.toMap(Lead::getId, l -> l));
        return list.stream().map(b -> {
            Lead l = leads.get(b.getLeadId());
            DemoSlot s = slots.get(b.getSlotId());
            return new BookingResponse(b.getId(), b.getSlotId(), s == null ? null : s.getStartsAt(), b.getLeadId(),
                    l == null ? null : l.getName(), l == null ? null : l.getPhone(), l == null ? null : l.getStatus(),
                    b.getStatus(), b.getCreatedAt());
        }).toList();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
