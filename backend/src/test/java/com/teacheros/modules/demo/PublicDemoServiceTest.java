package com.teacheros.modules.demo;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.course.CourseRepository;
import com.teacheros.modules.demo.dto.DemoDtos.PublicDemoBookingRequest;
import com.teacheros.modules.lead.Lead;
import com.teacheros.modules.lead.LeadRepository;
import com.teacheros.modules.lead.LeadSource;
import com.teacheros.modules.lead.LeadStatusHistoryRepository;
import com.teacheros.modules.lead.PublicEnquiryRateLimiter;
import com.teacheros.modules.tenant.PublicTenantResolver;
import com.teacheros.modules.tenant.Tenant;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicDemoServiceTest {

    @Mock
    PublicTenantResolver tenantResolver;
    @Mock
    DemoSlotRepository slotRepository;
    @Mock
    DemoBookingRepository bookingRepository;
    @Mock
    LeadRepository leadRepository;
    @Mock
    LeadStatusHistoryRepository historyRepository;
    @Mock
    CourseRepository courseRepository;
    @Mock
    DemoBookingCore core;
    @Mock
    PublicEnquiryRateLimiter rateLimiter;

    PublicDemoService service;
    final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PublicDemoService(tenantResolver, slotRepository, bookingRepository, leadRepository,
                historyRepository, courseRepository, core, rateLimiter);
    }

    private PublicDemoBookingRequest request(UUID slotId, String honeypot) {
        return new PublicDemoBookingRequest("school-a", slotId, "Asha Rao", "+91 98765 43210", null, honeypot);
    }

    @Test
    void honeypotHit_storesNothing() {
        when(rateLimiter.tryAcquire("demo-ip:1.1.1.1")).thenReturn(true);

        assertNull(service.book(request(UUID.randomUUID(), "http://spam.example"), "1.1.1.1"));
        verifyNoInteractions(tenantResolver, leadRepository, core);
    }

    @Test
    void overTheRateLimit_isRefused() {
        when(rateLimiter.tryAcquire("demo-ip:1.1.1.1")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.book(request(UUID.randomUUID(), null), "1.1.1.1"));
        assertEquals("RATE_LIMITED", ex.getCode());
        verifyNoInteractions(core);
    }

    @Test
    void existingLeadWithSamePhone_isReusedNotDuplicated() {
        Tenant tenant = new Tenant("School A");
        tenant.setId(tenantId);
        DemoSlot slot = new DemoSlot(tenantId, Instant.now().plus(1, ChronoUnit.DAYS), 30, 5);
        slot.setId(UUID.randomUUID());
        Lead existing = new Lead(tenantId, "Asha Rao", "+919876543210", null, null, LeadSource.WEBSITE);
        existing.setId(UUID.randomUUID());

        when(rateLimiter.tryAcquire("demo-ip:1.1.1.1")).thenReturn(true);
        when(tenantResolver.resolve("school-a")).thenReturn(tenant);
        when(slotRepository.findByIdAndTenantId(slot.getId(), tenantId)).thenReturn(Optional.of(slot));
        when(leadRepository.findFirstByTenantIdAndPhoneOrderByCreatedAtDesc(tenantId, "+919876543210"))
                .thenReturn(Optional.of(existing));
        when(core.book(tenantId, existing, slot.getId(), null))
                .thenReturn(
                        new DemoBookingCore.Booked(new DemoBooking(tenantId, slot.getId(), existing.getId()), slot));

        assertNotNull(service.book(request(slot.getId(), null), "1.1.1.1"));
        verify(leadRepository, never()).save(any());
    }
}
