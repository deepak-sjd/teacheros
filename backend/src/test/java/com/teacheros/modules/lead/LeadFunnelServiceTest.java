package com.teacheros.modules.lead;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.demo.DemoBookingRepository;
import com.teacheros.modules.demo.dto.FunnelDtos.FunnelResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadFunnelServiceTest {

    @Mock
    LeadRepository leadRepository;
    @Mock
    DemoBookingRepository demoBookingRepository;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    private LeadRepository.StatusCount stage(LeadStatus s, long n) {
        return new LeadRepository.StatusCount() {
            public LeadStatus getStatus() {
                return s;
            }

            public long getTotal() {
                return n;
            }
        };
    }

    private LeadRepository.SourceStatusCount src(LeadSource source, LeadStatus s, long n) {
        return new LeadRepository.SourceStatusCount() {
            public LeadSource getSource() {
                return source;
            }

            public LeadStatus getStatus() {
                return s;
            }

            public long getTotal() {
                return n;
            }
        };
    }

    @Test
    void countsAreZeroFilled_andConversionIsEnrolledOverTotal() {
        LeadFunnelService service = new LeadFunnelService(leadRepository, demoBookingRepository, currentTenantProvider);
        UUID tenantId = UUID.randomUUID();
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        when(leadRepository.countByStatusBetween(any(), any(), any())).thenReturn(List.of(
                stage(LeadStatus.NEW, 3), stage(LeadStatus.CONTACTED, 2), stage(LeadStatus.ENROLLED, 1)));
        when(leadRepository.countBySourceAndStatusBetween(any(), any(), any())).thenReturn(List.of(
                src(LeadSource.WEBSITE, LeadStatus.NEW, 3), src(LeadSource.WEBSITE, LeadStatus.ENROLLED, 1),
                src(LeadSource.WALK_IN, LeadStatus.CONTACTED, 2)));
        when(demoBookingRepository.countDistinctLeads(any(), any(), any(), any())).thenReturn(4L, 2L);

        FunnelResponse f = service.funnel(null, null);

        assertEquals(6, f.totalLeads());
        assertEquals(0L, f.byStage().get(LeadStatus.LOST)); // zero-filled
        assertEquals(5, f.byStage().size());
        assertEquals(16.7, f.conversionRate()); // 1 of 6
        assertEquals(4, f.demosBooked());
        assertEquals(2, f.demosAttended());
        assertEquals(LeadSource.WEBSITE, f.bySource().get(0).source()); // biggest source first
        assertEquals(25.0, f.bySource().get(0).conversionRate()); // 1 of 4
    }

    @Test
    void invertedRange_isRejected() {
        LeadFunnelService service = new LeadFunnelService(leadRepository, demoBookingRepository, currentTenantProvider);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.funnel(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertEquals("INVALID_RANGE", ex.getCode());
    }
}
