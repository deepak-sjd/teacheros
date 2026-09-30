package com.teacheros.modules.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dev default (app.payment.provider=mock, or unset). Lets the whole
 * pay-a-fee flow be built and tested end to end - order creation, the
 * frontend's "Pay now" screen, fee balance updating - without signing up
 * for Razorpay at all. verifySignature always succeeds because there's no
 * real signature to check; the frontend only ever calls verify here after
 * the person clicks the mock's own "Simulate successful payment" button
 * (see PaymentController's provider field), so there's no real money and
 * nothing to forge.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public OrderResult createOrder(long amountInPaise, String currency, String receiptId) {
        return new OrderResult("mock_order_" + UUID.randomUUID(), amountInPaise, currency);
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return true;
    }
}
