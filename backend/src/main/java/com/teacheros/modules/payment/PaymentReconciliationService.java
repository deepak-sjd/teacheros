package com.teacheros.modules.payment;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.payment.dto.PaymentDtos.ReconciliationItem;
import com.teacheros.modules.payment.dto.PaymentDtos.ReconciliationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Compares our own records (payment_orders vs fee_payments). It does not
 * call Razorpay, so an order that was paid at the gateway but never
 * reached us shows up under staleCreated, not paidNotRecorded.
 */
@Service
public class PaymentReconciliationService {

    private static final Duration STALE_AFTER = Duration.ofHours(1);

    private final PaymentOrderRepository paymentOrderRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public PaymentReconciliationService(PaymentOrderRepository paymentOrderRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public ReconciliationResponse report() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        return new ReconciliationResponse(
                paymentOrderRepository.findPaidWithoutPayment(tenantId).stream()
                        .map(this::fromOrder).toList(),
                paymentOrderRepository.findPaymentsWithoutPaidOrder(tenantId).stream()
                        .map(p -> new ReconciliationItem(p.getFeeId(), p.getGatewayOrderId(),
                                p.getGatewayPaymentId(), p.getAmountPaid(), p.getPaidAt()))
                        .toList(),
                paymentOrderRepository.findStaleCreated(tenantId, Instant.now().minus(STALE_AFTER)).stream()
                        .map(this::fromOrder).toList());
    }

    private ReconciliationItem fromOrder(PaymentOrder o) {
        return new ReconciliationItem(o.getFeeId(), o.getGatewayOrderId(), null,
                BigDecimal.valueOf(o.getAmountInPaise()).movePointLeft(2), o.getCreatedAt());
    }
}
