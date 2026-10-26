package com.teacheros.modules.demo;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.lead.Lead;
import com.teacheros.modules.lead.LeadRepository;
import com.teacheros.modules.lead.LeadSource;
import com.teacheros.modules.lead.LeadStatus;
import com.teacheros.modules.lead.LeadStatusHistoryRepository;
import com.teacheros.modules.notification.SmsSender;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoBookingCoreTest {

    @Mock
    DemoSlotRepository slots;
    @Mock
    DemoBookingRepository bookings;
    @Mock
    LeadRepository leads;
    @Mock
    LeadStatusHistoryRepository history;
    @Mock
    TenantRepository tenants;
    @Mock
    SmsSender sms;

    DemoBookingCore core;
    final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        core = new DemoBookingCore(slots, bookings, leads, history, tenants, sms);
        lenient().when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("School A")));
        lenient().when(bookings.save(any(DemoBooking.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bookings.findByTenantIdAndSlotIdAndLeadId(any(), any(), any())).thenReturn(Optional.empty());
    }

    private DemoSlot slot(int capacity, long startsInHours) {
        DemoSlot s = new DemoSlot(tenantId, Instant.now().plus(startsInHours, ChronoUnit.HOURS), 30, capacity);
        s.setId(UUID.randomUUID());
        lenient().when(slots.lockByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));
        return s;
    }

    private Lead lead(LeadStatus status) {
        Lead l = new Lead(tenantId, "Asha Rao", "+919876543210", null, null, LeadSource.WEBSITE);
        l.setId(UUID.randomUUID());
        l.setStatus(status);
        lenient().when(leads.findByIdAndTenantId(l.getId(), tenantId)).thenReturn(Optional.of(l));
        return l;
    }

    @Test
    void book_whenSlotIsFull_isRefused() {
        DemoSlot s = slot(1, 24);
        when(bookings.countByTenantIdAndSlotIdAndStatusNot(tenantId, s.getId(), DemoBookingStatus.CANCELLED))
                .thenReturn(1L);

        ApiException ex = assertThrows(ApiException.class,
                () -> core.book(tenantId, lead(LeadStatus.NEW), s.getId(), null));
        assertEquals("SLOT_FULL", ex.getCode());
        verify(bookings, never()).save(any());
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void book_pastSlot_isRefused() {
        DemoSlot s = slot(5, -1);
        ApiException ex = assertThrows(ApiException.class,
                () -> core.book(tenantId, lead(LeadStatus.NEW), s.getId(), null));
        assertEquals("SLOT_PAST", ex.getCode());
    }

    @Test
    void book_cancelledSlot_isNotFound() {
        DemoSlot s = slot(5, 24);
        s.setCancelled(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> core.book(tenantId, lead(LeadStatus.NEW), s.getId(), null));
        assertEquals("SLOT_NOT_FOUND", ex.getCode());
    }

    @Test
    void book_movesNewLeadToDemo_andTextsConfirmation() {
        DemoSlot s = slot(5, 24);
        Lead l = lead(LeadStatus.NEW);
        when(bookings.countByTenantIdAndSlotIdAndStatusNot(tenantId, s.getId(), DemoBookingStatus.CANCELLED))
                .thenReturn(0L);

        DemoBookingCore.Booked booked = core.book(tenantId, l, s.getId(), null);

        assertEquals(DemoBookingStatus.BOOKED, booked.booking().getStatus());
        assertEquals(LeadStatus.DEMO, l.getStatus());
        verify(sms).send(any(String.class), any(String.class));
    }

    @Test
    void book_sameLeadTwice_isIdempotent() {
        DemoSlot s = slot(5, 24);
        Lead l = lead(LeadStatus.DEMO);
        DemoBooking existing = new DemoBooking(tenantId, s.getId(), l.getId());
        when(bookings.findByTenantIdAndSlotIdAndLeadId(tenantId, s.getId(), l.getId()))
                .thenReturn(Optional.of(existing));

        DemoBookingCore.Booked booked = core.book(tenantId, l, s.getId(), null);

        assertEquals(existing, booked.booking());
        verify(bookings, never()).countByTenantIdAndSlotIdAndStatusNot(any(), any(), any());
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void noShow_movesLeadBackToContacted_andSchedulesFollowUp() {
        Lead l = lead(LeadStatus.DEMO);
        DemoBooking b = new DemoBooking(tenantId, UUID.randomUUID(), l.getId());

        core.applyAttendance(b, DemoBookingStatus.NO_SHOW, UUID.randomUUID());

        assertEquals(DemoBookingStatus.NO_SHOW, b.getStatus());
        assertEquals(LeadStatus.CONTACTED, l.getStatus());
        assertNotNull(l.getNextFollowUp());
    }

    @Test
    void showed_keepsLeadInDemo_andSchedulesFollowUp() {
        Lead l = lead(LeadStatus.CONTACTED);
        DemoBooking b = new DemoBooking(tenantId, UUID.randomUUID(), l.getId());

        core.applyAttendance(b, DemoBookingStatus.SHOWED, UUID.randomUUID());

        assertEquals(LeadStatus.DEMO, l.getStatus());
        assertNotNull(l.getNextFollowUp());
    }

    @Test
    void attendance_onEnrolledLead_leavesTheLeadAlone() {
        Lead l = lead(LeadStatus.ENROLLED);
        DemoBooking b = new DemoBooking(tenantId, UUID.randomUUID(), l.getId());

        core.applyAttendance(b, DemoBookingStatus.NO_SHOW, null);

        assertEquals(LeadStatus.ENROLLED, l.getStatus());
        verify(leads, never()).save(any());
    }
}
