package com.teacheros.modules.demo;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.lead.Lead;
import com.teacheros.modules.lead.LeadRepository;
import com.teacheros.modules.lead.LeadStatus;
import com.teacheros.modules.lead.LeadStatusHistory;
import com.teacheros.modules.lead.LeadStatusHistoryRepository;
import com.teacheros.modules.notification.SmsSender;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Booking rules shared by the staff and public paths. Callers must already be
 * in a transaction:
 * book() takes a row lock on the slot that is held until that transaction ends,
 * which is what
 * stops two visitors taking the last seat.
 */
@Component
public class DemoBookingCore {

    private static final Logger log = LoggerFactory.getLogger(DemoBookingCore.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, d MMM 'at' h:mm a", Locale.ENGLISH);
    private static final Set<LeadStatus> OPEN = EnumSet.of(LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.DEMO);

    public record Booked(DemoBooking booking, DemoSlot slot) {
    }

    private final DemoSlotRepository slots;
    private final DemoBookingRepository bookings;
    private final LeadRepository leads;
    private final LeadStatusHistoryRepository history;
    private final TenantRepository tenants;
    private final SmsSender smsSender;

    public DemoBookingCore(DemoSlotRepository slots, DemoBookingRepository bookings, LeadRepository leads,
            LeadStatusHistoryRepository history, TenantRepository tenants, SmsSender smsSender) {
        this.slots = slots;
        this.bookings = bookings;
        this.leads = leads;
        this.history = history;
        this.tenants = tenants;
        this.smsSender = smsSender;
    }

    /** changedByUserId is null for a public (logged-out) booking. */
    public Booked book(UUID tenantId, Lead lead, UUID slotId, UUID changedByUserId) {
        DemoSlot slot = slots.lockByIdAndTenantId(slotId, tenantId)
                .filter(s -> !s.isCancelled())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND",
                        "That demo slot isn't available."));
        if (!slot.getStartsAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_PAST", "That demo has already started.");
        }

        DemoBooking existing = bookings.findByTenantIdAndSlotIdAndLeadId(tenantId, slotId, lead.getId()).orElse(null);
        if (existing != null && existing.getStatus() != DemoBookingStatus.CANCELLED) {
            return new Booked(existing, slot); // same person booking twice: idempotent, no second SMS
        }

        long taken = bookings.countByTenantIdAndSlotIdAndStatusNot(tenantId, slotId, DemoBookingStatus.CANCELLED);
        if (taken >= slot.getCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_FULL", "That slot is full. Please pick another time.");
        }

        DemoBooking booking = existing != null ? existing : new DemoBooking(tenantId, slotId, lead.getId());
        booking.setStatus(DemoBookingStatus.BOOKED);
        booking = bookings.save(booking);

        String when = WHEN.format(slot.getStartsAt().atZone(APP_ZONE));
        if (lead.getStatus() == LeadStatus.NEW || lead.getStatus() == LeadStatus.CONTACTED
                || lead.getStatus() == LeadStatus.LOST) {
            moveLead(lead, LeadStatus.DEMO, "Demo booked for " + when, changedByUserId);
        }

        String where = slot.getLocation() == null || slot.getLocation().isBlank() ? "" : " at " + slot.getLocation();
        sendAfterCommit(lead.getPhone(), "Hi %s, your demo class at %s is confirmed for %s IST%s."
                .formatted(firstName(lead.getName()), schoolName(tenantId), when, where));
        return new Booked(booking, slot);
    }

    /**
     * result must be SHOWED or NO_SHOW; the caller validates. Moves the lead and
     * schedules a follow-up.
     */
    public void applyAttendance(DemoBooking booking, DemoBookingStatus result, UUID changedByUserId) {
        booking.setStatus(result);
        bookings.save(booking);

        Lead lead = leads.findByIdAndTenantId(booking.getLeadId(), booking.getTenantId()).orElse(null);
        if (lead == null || !OPEN.contains(lead.getStatus())) {
            return; // enrolled / lost leads are left alone
        }
        LeadStatus from = lead.getStatus();
        LeadStatus to = result == DemoBookingStatus.SHOWED ? LeadStatus.DEMO : LeadStatus.CONTACTED;
        String note = result == DemoBookingStatus.SHOWED ? "Attended demo" : "No-show for demo";

        LocalDate tomorrow = LocalDate.now(APP_ZONE).plusDays(1);
        if (lead.getNextFollowUp() == null || lead.getNextFollowUp().isAfter(tomorrow)) {
            lead.setNextFollowUp(tomorrow);
        }
        lead.setStatus(to);
        leads.save(lead);
        history.save(new LeadStatusHistory(booking.getTenantId(), lead.getId(), from, to, note, changedByUserId));
    }

    /**
     * Cancels one booking; if it was the lead's only active demo, a DEMO lead goes
     * back to CONTACTED.
     */
    public void cancelBooking(DemoBooking booking, DemoSlot slot, UUID changedByUserId, boolean notify) {
        booking.setStatus(DemoBookingStatus.CANCELLED);
        bookings.save(booking);

        Lead lead = leads.findByIdAndTenantId(booking.getLeadId(), booking.getTenantId()).orElse(null);
        if (lead == null)
            return;

        boolean stillBooked = bookings.existsByTenantIdAndLeadIdAndStatusNot(
                booking.getTenantId(), lead.getId(), DemoBookingStatus.CANCELLED);
        if (lead.getStatus() == LeadStatus.DEMO && !stillBooked) {
            moveLead(lead, LeadStatus.CONTACTED, "Demo cancelled", changedByUserId);
        }
        if (notify) {
            sendAfterCommit(lead.getPhone(),
                    "Hi %s, your demo class on %s IST has been cancelled. We'll be in touch to reschedule."
                            .formatted(firstName(lead.getName()), WHEN.format(slot.getStartsAt().atZone(APP_ZONE))));
        }
    }

    private void moveLead(Lead lead, LeadStatus to, String note, UUID changedByUserId) {
        LeadStatus from = lead.getStatus();
        lead.setStatus(to);
        leads.save(lead);
        history.save(new LeadStatusHistory(lead.getTenantId(), lead.getId(), from, to, note, changedByUserId));
    }

    private String schoolName(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getName).orElse("our centre");
    }

    private String firstName(String name) {
        String trimmed = name == null ? "" : name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : (trimmed.isEmpty() ? "there" : trimmed);
    }

    // Send only once the booking is committed, so a rolled-back booking never texts
    // anyone.
    private void sendAfterCommit(String phone, String message) {
        Runnable send = () -> {
            try {
                smsSender.send(phone, message);
            } catch (Exception e) {
                log.error("Failed to send demo SMS to {}: {}", phone, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
