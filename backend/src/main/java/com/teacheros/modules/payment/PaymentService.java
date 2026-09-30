package com.teacheros.modules.payment;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.fee.FeeService;
import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderRequest;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * No @PreAuthorize here or on PaymentController - same pattern as
 * FeeController's /fees/students/{id} and MessageController: open to any
 * authenticated role, with StudentAccessGuard enforcing "your own fee, or
 * your own child's fee" for PARENT/STUDENT. A staff member paying on
 * someone's behalf isn't supported here - they use the existing manual
 * "Record payment" flow (FeeController#recordPayment) instead.
 */
@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final FeeService feeService;
    private final StudentAccessGuard studentAccessGuard;
    private final String currency;
    private final String razorpayKeyId;

    public PaymentService(
            PaymentGateway paymentGateway,
            FeeService feeService,
            StudentAccessGuard studentAccessGuard,
            @Value("${app.payment.currency:INR}") String currency,
            @Value("${app.payment.razorpay.key-id:}") String razorpayKeyId) {
        this.paymentGateway = paymentGateway;
        this.feeService = feeService;
        this.studentAccessGuard = studentAccessGuard;
        this.currency = currency;
        this.razorpayKeyId = razorpayKeyId;
    }

    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        FeeResponse fee = feeService.get(request.feeId());
        studentAccessGuard.assertCanView(fee.studentId());

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

        return new CreateOrderResponse(paymentGateway.getProviderName(), order.orderId(),
                order.amountInPaise(), order.currency(), razorpayKeyId.isBlank() ? null : razorpayKeyId,
                request.feeId());
    }

    public FeeResponse verify(VerifyPaymentRequest request) {
        FeeResponse fee = feeService.get(request.feeId());
        studentAccessGuard.assertCanView(fee.studentId());

        boolean valid = paymentGateway.verifySignature(request.orderId(), request.paymentId(), request.signature());
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_VERIFICATION_FAILED",
                    "This payment could not be verified. If money was deducted, contact your teacher.");
        }

        // The order's own amount is the source of truth for how much to
        // credit, not anything the frontend sends here - VerifyPaymentRequest
        // deliberately carries no amount field, so a tampered client request
        // can't credit an arbitrary figure. Since orders aren't persisted
        // (see PaymentService's class notes / PaymentGateway), the amount
        // actually paid is exactly the outstanding balance at order-creation
        // time; re-deriving that here would need order storage this app
        // doesn't have yet, so this credits the fee's full outstanding
        // balance as of now. Fine for the common case (paying in full) -
        // partial-payment-then-abandon-and-repay flows aren't handled yet.
        BigDecimal outstanding = fee.amountDue().subtract(fee.amountPaid());

        return feeService.recordGatewayPayment(request.feeId(), outstanding, paymentGateway.getProviderName(),
                request.orderId(), request.paymentId());
    }

}
