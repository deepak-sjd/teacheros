package com.teacheros.modules.payment;

import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real Razorpay integration. Works identically with Razorpay's free test-mode
 * keys (rzp_test_...) or live keys (rzp_live_...) - which one is active
 * depends entirely on which keys are configured, not on any code here. Test
 * mode is free forever and processes fake card/UPI payments; switching to
 * live mode is a config change on Razorpay's dashboard + swapping the key
 * pair, nothing in this class changes.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final RazorpayClient client;
    private final String keySecret;

    public RazorpayPaymentGateway(
            @Value("${app.payment.razorpay.key-id}") String keyId,
            @Value("${app.payment.razorpay.key-secret}") String keySecret) throws Exception {
        this.client = new RazorpayClient(keyId, keySecret);
        this.keySecret = keySecret;
    }

    @Override
    public String getProviderName() {
        return "razorpay";
    }

    @Override
    public OrderResult createOrder(long amountInPaise, String currency, String receiptId) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountInPaise);
            request.put("currency", currency);
            request.put("receipt", receiptId);
            var order = client.orders.create(request);
            return new OrderResult(order.get("id"), amountInPaise, currency);
        } catch (Exception e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new com.teacheros.common.exception.ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_GATEWAY_UNAVAILABLE",
                    "Could not start the payment. Please try again shortly.");
        }
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("razorpay_order_id", orderId);
            payload.put("razorpay_payment_id", paymentId);
            payload.put("razorpay_signature", signature);
            return Utils.verifyPaymentSignature(payload, keySecret);
        } catch (Exception e) {
            log.error("Razorpay signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
