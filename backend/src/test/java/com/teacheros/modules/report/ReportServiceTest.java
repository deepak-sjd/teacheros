package com.teacheros.modules.report;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.AssessmentRepository;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.fee.FeePaymentRepository;
import com.teacheros.modules.fee.FeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    CurrentTenantProvider tenantProvider;
    @Mock
    BatchRepository batchRepository;
    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    FeeRepository feeRepository;
    @Mock
    FeePaymentRepository feePaymentRepository;
    @Mock
    AssessmentRepository assessmentRepository;
    @Mock
    MarkRepository markRepository;

    @Test
    void feeSummary_rangeIsInclusiveIstDay_soAPaymentAt0030IstIsCounted() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT);
        ReportService service = new ReportService(tenantProvider, batchRepository, attendanceRepository,
                feeRepository, feePaymentRepository, assessmentRepository, markRepository);

        LocalDate day = LocalDate.of(2026, 3, 10);
        service.generate(day, day, null);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(feePaymentRepository).sumCollectedBetween(eq(TENANT), from.capture(), to.capture());

        assertEquals(Instant.parse("2026-03-09T18:30:00Z"), from.getValue());
        assertEquals(Instant.parse("2026-03-10T18:30:00Z"), to.getValue());

        Instant paidAt0030Ist = Instant.parse("2026-03-09T19:00:00Z");
        assertTrue(!paidAt0030Ist.isBefore(from.getValue()) && paidAt0030Ist.isBefore(to.getValue()));

        Instant paidAt2359IstPrevDay = Instant.parse("2026-03-09T18:29:59Z");
        assertFalse(!paidAt2359IstPrevDay.isBefore(from.getValue()));
    }
}
