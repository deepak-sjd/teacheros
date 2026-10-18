package com.teacheros.modules.ai;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.ai.AiUsageRepository.DailyUsageRow;
import com.teacheros.modules.ai.dto.AiUsageDtos.AiFeatureSummary;
import com.teacheros.modules.ai.dto.AiUsageDtos.AiUsageDashboardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiUsageReportServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    // 00:30 IST on 10 March 2026.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-09T19:00:00Z"), ZoneOffset.UTC);

    @Mock
    AiUsageRepository repository;
    @Mock
    AiUsageService aiUsageService;
    @Mock
    CurrentTenantProvider tenantProvider;

    private AiUsageReportService service;

    @BeforeEach
    void setUp() {
        lenient().when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT);
        lenient().when(aiUsageService.dailyLimit(any())).thenReturn(300);
        service = new AiUsageReportService(repository, aiUsageService, tenantProvider,
                new BigDecimal("3.00"), new BigDecimal("15.00"), "anthropic", "USD", CLOCK);
    }

    @Test
    void costsOnlyTheBilledProvider_andComputesFailureRate() {
        when(repository.aggregateByDay(eq(TENANT), any(), any())).thenReturn(List.of(
                row("2026-03-10", "STUDY_PLAN", "anthropic", "SUCCESS", 3, 1_000_000, 1_000_000),
                row("2026-03-10", "STUDY_PLAN", "anthropic", "FAILED", 1, 0, 0),
                row("2026-03-10", "DOUBT", "ollama", "SUCCESS", 5, 900_000, 900_000)));

        AiUsageDashboardResponse res = service.report(30);

        assertEquals(9, res.totalCalls());
        assertEquals(1, res.totalFailed());
        assertEquals(11.1, res.failureRate());
        // 1M in * $3 + 1M out * $15 = $18; ollama is free.
        assertEquals(0, new BigDecimal("18").compareTo(res.estimatedCost()));

        AiFeatureSummary plan = res.features().stream().filter(f -> f.feature().equals("STUDY_PLAN")).findFirst()
                .orElseThrow();
        assertEquals(4, plan.calls());
        assertEquals(1, plan.failed());
        assertEquals(25.0, plan.failureRate());
        assertEquals(300, plan.dailyLimit());

        AiFeatureSummary doubt = res.features().stream().filter(f -> f.feature().equals("DOUBT")).findFirst()
                .orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(doubt.estimatedCost()));
    }

    @Test
    void rangeIsInclusiveIstDays_andCappedAt90() {
        AiUsageDashboardResponse res = service.report(1000);

        assertEquals(LocalDate.of(2026, 3, 10), res.to());
        assertEquals(LocalDate.of(2026, 3, 10).minusDays(89), res.from());

        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(repository).aggregateByDay(eq(TENANT), any(), to.capture());
        assertEquals(Instant.parse("2026-03-10T18:30:00Z"), to.getValue()); // 11 Mar 00:00 IST
    }

    @Test
    void featuresWithNoUsageStillAppearWithZeros() {
        AiUsageDashboardResponse res = service.report(7);
        assertEquals(AiFeature.values().length, res.features().size());
        assertEquals(0, res.totalCalls());
        assertEquals(0.0, res.failureRate());
    }

    private DailyUsageRow row(String day, String feature, String provider, String status, long calls, long in,
            long out) {
        DailyUsageRow r = mock(DailyUsageRow.class);
        when(r.getDay()).thenReturn(day);
        when(r.getFeature()).thenReturn(feature);
        when(r.getProvider()).thenReturn(provider);
        when(r.getStatus()).thenReturn(status);
        when(r.getCalls()).thenReturn(calls);
        when(r.getInputTokens()).thenReturn(in);
        when(r.getOutputTokens()).thenReturn(out);
        return r;
    }
}
