package com.teacheros.modules.payment;

/**
 * Abstraction over "create an order, verify a completed payment's
 * signature" so the app can run fully free (MockPaymentGateway, no
 * Razorpay account needed) during development, and switch to
 * RazorpayPaymentGateway for real by changing one config value
 * (app.payment.provider) - no code or frontend changes required, since
 * OrderResult carries a "provider" field the frontend uses to decide
 * whether to open the real Razorpay checkout or just simulate success.
 */
public interface PaymentGateway {

    String getProviderName();

    OrderResult createOrder(long amountInPaise, String currency, String receiptId);

    boolean verifySignature(String orderId, String paymentId, String signature);

    record OrderResult(String orderId, long amountInPaise, String currency) {
    }
}
