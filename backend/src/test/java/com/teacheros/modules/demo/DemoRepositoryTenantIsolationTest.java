package com.teacheros.modules.demo;

import com.teacheros.modules.lead.Lead;
import com.teacheros.modules.lead.LeadRepository;
import com.teacheros.modules.lead.LeadSource;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DemoRepositoryTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DemoSlotRepository slotRepository;
    @Autowired
    DemoBookingRepository bookingRepository;
    @Autowired
    LeadRepository leadRepository;
    @Autowired
    TenantRepository tenantRepository;

    @Test
    void anotherTenantCannotSeeASlotOrItsBookings() {
        Tenant a = tenantRepository.save(new Tenant("School A"));
        Tenant b = tenantRepository.save(new Tenant("School B"));
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);

        DemoSlot slot = slotRepository.save(new DemoSlot(a.getId(), start, 30, 5));
        Lead lead = leadRepository.save(new Lead(a.getId(), "Asha", "+919876543210", null, null, LeadSource.WEBSITE));
        DemoBooking booking = bookingRepository.save(new DemoBooking(a.getId(), slot.getId(), lead.getId()));

        assertTrue(slotRepository.findByIdAndTenantId(slot.getId(), b.getId()).isEmpty());
        assertTrue(slotRepository.lockByIdAndTenantId(slot.getId(), b.getId()).isEmpty());
        assertTrue(slotRepository.findByTenantIdAndStartsAtBetweenOrderByStartsAtAsc(
                b.getId(), start.minus(1, ChronoUnit.DAYS), start.plus(1, ChronoUnit.DAYS)).isEmpty());
        assertTrue(slotRepository.findByTenantIdAndCancelledFalseAndStartsAtGreaterThanOrderByStartsAtAsc(
                b.getId(), Instant.now()).isEmpty());
        assertTrue(bookingRepository.findByIdAndTenantId(booking.getId(), b.getId()).isEmpty());
        assertTrue(bookingRepository.findByTenantIdAndSlotIdOrderByCreatedAtAsc(b.getId(), slot.getId()).isEmpty());
        assertTrue(bookingRepository.countActiveBySlot(b.getId(), List.of(slot.getId())).isEmpty());
        assertEquals(0, bookingRepository.countByTenantIdAndSlotIdAndStatusNot(
                b.getId(), slot.getId(), DemoBookingStatus.CANCELLED));

        assertTrue(slotRepository.lockByIdAndTenantId(slot.getId(), a.getId()).isPresent());
        assertEquals(1, bookingRepository.countByTenantIdAndSlotIdAndStatusNot(
                a.getId(), slot.getId(), DemoBookingStatus.CANCELLED));
    }
}
