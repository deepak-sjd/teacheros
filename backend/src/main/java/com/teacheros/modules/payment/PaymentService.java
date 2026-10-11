package com.teacheros.modules.payment;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.fee.FeeService;
import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderRequest;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * No @PreAuthorize here or on PaymentController - same pattern as
 * FeeController's /fees/students/{id} and MessageController: open to any
 * authenticated role, with StudentAccessGuard enforcing "your own fee, or
 * your own child's fee" for PARENT/STUDENT. A staff member paying on
 * someone's behalf isn't supported here - they use the existing manual
 * "Record payment" flow (FeeController#recordPayment) instead.
 *
 * Every order is persisted (payment_orders). verify() credits the ORDER's
 * amount, never the fee's current outstanding balance, so a partial order
 * or an abandoned-and-recreated order can't credit the wrong figure.
 */
@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final FeeService feeService;
    private final StudentAccessGuard studentAccessGuard;
    private final PaymentOrderRepository paymentOrderRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final String currency;
    private final String razorpayKeyId;

    public PaymentService(
            PaymentGateway paymentGateway,
            FeeService feeService,
            StudentAccessGuard studentAccessGuard,
            PaymentOrderRepository paymentOrderRepository,
            CurrentTenantProvider currentTenantProvider,
            @Value("${app.payment.currency:INR}") String currency,
            @Value("${app.payment.razorpay.key-id:}") String razorpayKeyId) {
        this.paymentGateway = paymentGateway;
        this.feeService = feeService;
        this.studentAccessGuard = studentAccessGuard;
        this.paymentOrderRepository = paymentOrderRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.currency = currency;
        this.razorpayKeyId = razorpayKeyId;
    }

    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        FeeResponse fee = feeService.get(request.feeId());
        studentAccessGuard.assertCanView(fee.studentId());
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        BigDecimal outstanding = fee.amountDue().subtract(fee.amountPaid());
        if (request.amount().compareTo(outstanding) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_BALANCE",
                    "That's more than the outstanding balance of \u20b9" + outstanding);
        }

        // Razorpay (and every gateway) takes amounts in the smallest currency
        // unit - paise for INR, i.e. rupees * 100 - not rupees directly.
        long amountInPaise = request.amount().setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).longValueExact();

        PaymentGateway.OrderResult order = paymentGateway.createOrder(amountInPaise, currency,
                "fee_" + request.feeId());

        paymentOrderRepository.save(
                new PaymentOrder(tenantId, request.feeId(), order.orderId(), order.amountInPaise()));

        return new CreateOrderResponse(paymentGateway.getProviderName(), order.orderId(),
                order.amountInPaise(), order.currency(), razorpayKeyId.isBlank() ? null : razorpayKeyId,
                request.feeId());
    }

    @Transactional
    public FeeResponse verify(VerifyPaymentRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        // Tenant-scoped fee lookup + guard first: another tenant's fee ID is a 404.
        FeeResponse fee = feeService.get(request.feeId());
        studentAccessGuard.assertCanView(fee.studentId());

        // Another tenant's order ID, or an order that belongs to a different
        // fee, is a 404 - never confirms the order exists.
        PaymentOrder order = paymentOrderRepository.findForUpdate(tenantId, request.orderId())
                .filter(o -> o.getFeeId().equals(request.feeId()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment order", request.orderId()));

        boolean valid = paymentGateway.verifySignature(request.orderId(), request.paymentId(), request.signature());
        if (!valid) {
            // Deliberately NOT marking the order FAILED here: a forged call
            // must not be able to fail a legitimate order.
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_VERIFICATION_FAILED",
                    "This payment could not be verified. If money was deducted, contact your teacher.");
        }

        // One order is credited once, whatever paymentId a replay carries
        // (the mock gateway generates a fresh paymentId on every click).
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return feeService.get(request.feeId());
        }

        BigDecimal amount = BigDecimal.valueOf(order.getAmountInPaise()).movePointLeft(2);

        // recordGatewayPayment also dedupes on gatewayPaymentId (unique index).
        FeeResponse updated = feeService.recordGatewayPayment(request.feeId(), amount,
                paymentGateway.getProviderName(), request.orderId(), request.paymentId());

        order.setStatus(PaymentOrderStatus.PAID);
        paymentOrderRepository.save(order);
        return updated;
    }
}
