package com.teacheros.modules.payment;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.fee.FeeService;
import com.teacheros.modules.fee.FeeStatus;
import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.teacheros.modules.student.StudentAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID feeId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    private PaymentGateway gateway;
    private FeeService feeService;
    private PaymentOrderRepository orders;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        gateway = mock(PaymentGateway.class);
        feeService = mock(FeeService.class);
        orders = mock(PaymentOrderRepository.class);
        StudentAccessGuard guard = mock(StudentAccessGuard.class);
        CurrentTenantProvider tenant = mock(CurrentTenantProvider.class);

        when(tenant.getCurrentTenantId()).thenReturn(tenantId);
        when(gateway.getProviderName()).thenReturn("mock");
        when(gateway.verifySignature(anyString(), anyString(), any())).thenReturn(true);
        when(feeService.get(feeId)).thenReturn(fee("1000", "0"));

        service = new PaymentService(gateway, feeService, guard, orders, tenant, "INR", "");
    }

    private FeeResponse fee(String due, String paid) {
        return new FeeResponse(feeId, studentId, new BigDecimal(due), new BigDecimal(paid),
                LocalDate.now(), FeeStatus.PENDING);
    }

    private PaymentOrder order(long paise, PaymentOrderStatus status) {
        PaymentOrder o = new PaymentOrder(tenantId, feeId, "order_1", paise);
        o.setStatus(status);
        return o;
    }

    private VerifyPaymentRequest request() {
        return new VerifyPaymentRequest(feeId, "order_1", "pay_1", "sig");
    }

    @Test
    void partialOrderCreditsExactlyTheOrderAmount() {
        when(orders.findForUpdate(tenantId, "order_1"))
                .thenReturn(Optional.of(order(25_000, PaymentOrderStatus.CREATED)));

        service.verify(request());

        verify(feeService).recordGatewayPayment(feeId, new BigDecimal("250.00"), "mock", "order_1", "pay_1");
    }

    @Test
    void replayCreditsOnce() {
        PaymentOrder o = order(25_000, PaymentOrderStatus.CREATED);
        when(orders.findForUpdate(tenantId, "order_1")).thenReturn(Optional.of(o));

        service.verify(request());
        service.verify(new VerifyPaymentRequest(feeId, "order_1", "pay_2", "sig"));

        verify(feeService, times(1)).recordGatewayPayment(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void otherTenantsOrderIsNotFound() {
        when(orders.findForUpdate(tenantId, "order_1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.verify(request()));
        verify(feeService, never()).recordGatewayPayment(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void orderBelongingToAnotherFeeIsNotFound() {
        PaymentOrder o = new PaymentOrder(tenantId, UUID.randomUUID(), "order_1", 25_000);
        when(orders.findForUpdate(tenantId, "order_1")).thenReturn(Optional.of(o));

        assertThrows(ResourceNotFoundException.class, () -> service.verify(request()));
    }

    @Test
    void invalidSignatureCreditsNothing() {
        when(orders.findForUpdate(eq(tenantId), anyString()))
                .thenReturn(Optional.of(order(25_000, PaymentOrderStatus.CREATED)));
        when(gateway.verifySignature(anyString(), anyString(), any())).thenReturn(false);

        assertThrows(ApiException.class, () -> service.verify(request()));
        verify(feeService, never()).recordGatewayPayment(any(), any(), anyString(), anyString(), anyString());
    }
}
