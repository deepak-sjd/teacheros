package com.teacheros.modules.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.fee.FeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {

    private static final String SECRET = "whsec_test";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID feeId = UUID.randomUUID();

    private PaymentOrderRepository orders;
    private FeeService feeService;
    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        orders = mock(PaymentOrderRepository.class);
        feeService = mock(FeeService.class);
        service = new PaymentWebhookService(orders, feeService, new ObjectMapper(), SECRET);
    }

    private String body(String event, long amount) {
        return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":" + amount + "}}}}";
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private PaymentOrder order(PaymentOrderStatus status) {
        PaymentOrder o = new PaymentOrder(tenantId, feeId, "order_1", 25_000);
        o.setStatus(status);
        return o;
    }

    @Test
    void invalidSignatureIs400AndRecordsNothing() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.handle(body("payment.captured", 25_000), "bad"));

        org.junit.jupiter.api.Assertions.assertEquals(400, ex.getStatus().value());
        verifyNoInteractions(orders, feeService);
    }

    @Test
    void capturedCreditsOrderAmountAndMarksPaid() throws Exception {
        PaymentOrder o = order(PaymentOrderStatus.CREATED);
        when(orders.findForUpdateByGatewayOrderId("order_1")).thenReturn(Optional.of(o));
        String b = body("payment.captured", 25_000);

        service.handle(b, sign(b));

        verify(feeService).recordGatewayPaymentForTenant(tenantId, feeId, new BigDecimal("250.00"),
                "razorpay", "order_1", "pay_1");
        org.junit.jupiter.api.Assertions.assertEquals(PaymentOrderStatus.PAID, o.getStatus());
    }

    @Test
    void replayedCapturedCreditsOnce() throws Exception {
        when(orders.findForUpdateByGatewayOrderId("order_1"))
                .thenReturn(Optional.of(order(PaymentOrderStatus.CREATED)));
        String b = body("payment.captured", 25_000);

        service.handle(b, sign(b));
        service.handle(b, sign(b));

        verify(feeService, times(1)).recordGatewayPaymentForTenant(any(), any(), any(), anyString(), anyString(),
                anyString());
    }

    @Test
    void failedNeverDowngradesAPaidOrder() throws Exception {
        PaymentOrder o = order(PaymentOrderStatus.PAID);
        when(orders.findForUpdateByGatewayOrderId("order_1")).thenReturn(Optional.of(o));
        String b = body("payment.failed", 25_000);

        service.handle(b, sign(b));

        org.junit.jupiter.api.Assertions.assertEquals(PaymentOrderStatus.PAID, o.getStatus());
    }
}
