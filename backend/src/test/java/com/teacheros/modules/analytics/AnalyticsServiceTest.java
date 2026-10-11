package com.teacheros.modules.analytics;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.AttendanceTrendResponse;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.FeeCollectionTrendResponse;
import com.teacheros.modules.analytics.dto.AnalyticsDtos.SubjectPerformanceResponse;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.fee.FeePaymentRepository;
import com.teacheros.modules.fee.FeePaymentRepository.PaymentRow;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    // 2026-03-09T19:00:00Z == 00:30 IST on 10 March - a UTC server still says "9
    // March".
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-09T19:00:00Z"), ZoneOffset.UTC);

    @Mock
    CurrentTenantProvider tenantProvider;
    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    MarkRepository markRepository;
    @Mock
    BatchRepository batchRepository;
    @Mock
    FeePaymentRepository feePaymentRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        lenient().when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT);
        service = new AnalyticsService(tenantProvider, attendanceRepository, markRepository, batchRepository,
                feePaymentRepository, CLOCK);
    }

    @Test
    void attendanceTrend_usesIstToday() {
        AttendanceTrendResponse res = service.attendanceTrend(14, null);

        assertEquals(LocalDate.of(2026, 3, 10), res.to());
        assertEquals(LocalDate.of(2026, 2, 25), res.from());

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(attendanceRepository).findRecordsInRange(eq(TENANT), isNull(), from.capture(), to.capture());
        assertEquals(LocalDate.of(2026, 3, 10), to.getValue());
        assertEquals(LocalDate.of(2026, 2, 25), from.getValue());
    }

    @Test
    void subjectPerformance_usesIstToday() {
        SubjectPerformanceResponse res = service.subjectPerformance(7, null);

        assertEquals(LocalDate.of(2026, 3, 10), res.to());
        assertEquals(LocalDate.of(2026, 3, 4), res.from());
    }

    @Test
    void feeCollectionTrend_bucketsPaymentsByIstDay() {
        PaymentRow justAfterIstMidnight = row("2026-03-09T19:00:00Z", "500"); // 00:30 IST, 10 Mar
        PaymentRow justBeforeIstMidnight = row("2026-03-09T18:29:59Z", "200"); // 23:59:59 IST, 9 Mar
        when(feePaymentRepository.findPaymentsInRange(eq(TENANT), any(), any()))
                .thenReturn(List.of(justAfterIstMidnight, justBeforeIstMidnight));

        FeeCollectionTrendResponse res = service.feeCollectionTrend(2);

        assertEquals(LocalDate.of(2026, 3, 10), res.to());
        assertEquals(2, res.points().size());
        assertEquals(LocalDate.of(2026, 3, 9), res.points().get(0).date());
        assertEquals(new BigDecimal("200"), res.points().get(0).amountCollected());
        assertEquals(LocalDate.of(2026, 3, 10), res.points().get(1).date());
        assertEquals(new BigDecimal("500"), res.points().get(1).amountCollected());

        ArgumentCaptor<Instant> fromI = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toI = ArgumentCaptor.forClass(Instant.class);
        verify(feePaymentRepository).findPaymentsInRange(eq(TENANT), fromI.capture(), toI.capture());
        assertEquals(Instant.parse("2026-03-08T18:30:00Z"), fromI.getValue()); // 9 Mar 00:00 IST
        assertEquals(Instant.parse("2026-03-10T18:30:00Z"), toI.getValue()); // 11 Mar 00:00 IST
    }

    private PaymentRow row(String paidAt, String amount) {
        PaymentRow row = mock(PaymentRow.class);
        when(row.getPaidAt()).thenReturn(Instant.parse(paidAt));
        when(row.getAmountPaid()).thenReturn(new BigDecimal(amount));
        return row;
    }
}
