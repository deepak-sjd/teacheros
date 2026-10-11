package com.teacheros.modules.payment;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeePayment;
import com.teacheros.modules.fee.FeePaymentRepository;
import com.teacheros.modules.fee.FeeRepository;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ReceiptServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID feeId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private FeePaymentRepository payments;
    private FeeRepository fees;
    private StudentRepository students;
    private TenantRepository tenants;
    private StudentAccessGuard guard;
    private ReceiptService service;

    @BeforeEach
    void setUp() {
        payments = mock(FeePaymentRepository.class);
        fees = mock(FeeRepository.class);
        students = mock(StudentRepository.class);
        tenants = mock(TenantRepository.class);
        guard = mock(StudentAccessGuard.class);
        CurrentTenantProvider tenant = mock(CurrentTenantProvider.class);
        when(tenant.getCurrentTenantId()).thenReturn(tenantId);
        service = new ReceiptService(payments, fees, students, tenants, guard, tenant);
    }

    private FeePayment payment() {
        FeePayment p = new FeePayment(tenantId, feeId, new BigDecimal("250"), "Cash");
        p.setPaidAt(Instant.now());
        p.setReceiptNumber(7L);
        return p;
    }

    private Fee fee() {
        return new Fee(tenantId, studentId, new BigDecimal("1000"), LocalDate.now());
    }

    @Test
    void paymentOutsideTenantIsNotFound() {
        when(payments.findByIdAndTenantId(paymentId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.generatePdf(paymentId));
    }

    @Test
    void otherFamilyIsNotFoundAndNothingElseIsRead() {
        when(payments.findByIdAndTenantId(paymentId, tenantId)).thenReturn(Optional.of(payment()));
        when(fees.findByIdAndTenantId(feeId, tenantId)).thenReturn(Optional.of(fee()));
        doThrow(new ResourceNotFoundException("Student", studentId)).when(guard).assertCanView(studentId);

        assertThrows(ResourceNotFoundException.class, () -> service.generatePdf(paymentId));
        verifyNoInteractions(students, tenants);
    }

    @Test
    void ownReceiptIsAPdf() {
        when(payments.findByIdAndTenantId(paymentId, tenantId)).thenReturn(Optional.of(payment()));
        when(fees.findByIdAndTenantId(feeId, tenantId)).thenReturn(Optional.of(fee()));
        when(students.findByIdAndTenantId(studentId, tenantId))
                .thenReturn(Optional.of(new Student(tenantId, null, "Asha", "10", "9000000000")));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Sunrise Tuition")));

        byte[] pdf = service.generatePdf(paymentId);

        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
