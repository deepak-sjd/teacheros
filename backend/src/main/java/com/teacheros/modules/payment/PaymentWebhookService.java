package com.teacheros.modules.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.fee.FeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Razorpay webhook: credits a payment even if the browser was closed
 * before verify() ran. No JWT here - authenticity comes solely from the
 * HMAC signature, and the tenant is read from the stored PaymentOrder,
 * never from the request. Shares the order row lock with
 * PaymentService#verify, so webhook + verify racing credit once.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentOrderRepository paymentOrderRepository;
    private final FeeService feeService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public PaymentWebhookService(
            PaymentOrderRepository paymentOrderRepository,
            FeeService feeService,
            ObjectMapper objectMapper,
            @Value("${app.payment.razorpay.webhook-secret:}") String webhookSecret) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.feeService = feeService;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public void handle(String rawBody, String signature) {
        if (!validSignature(rawBody, signature)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Invalid webhook signature");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "Invalid webhook payload");
        }

        String event = root.path("event").asText("");
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String orderId = payment.path("order_id").asText("");
        String paymentId = payment.path("id").asText("");
        long amount = payment.path("amount").asLong(-1);

        if (orderId.isBlank() || paymentId.isBlank()) {
            return; // not a payment event we handle (e.g. order.paid) - ack so Razorpay doesn't
                    // retry
        }

        Optional<PaymentOrder> found = paymentOrderRepository.findForUpdateByGatewayOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("Webhook {} for unknown order {} - ignored", event, orderId);
            return;
        }
        PaymentOrder order = found.get();

        switch (event) {
            case "payment.captured" -> {
                if (order.getStatus() == PaymentOrderStatus.PAID) {
                    return; // already credited (verify or an earlier webhook)
                }
                if (amount != order.getAmountInPaise()) {
                    log.error("Webhook amount {} != order amount {} for order {} - NOT credited",
                            amount, order.getAmountInPaise(), orderId);
                    return;
                }
                feeService.recordGatewayPaymentForTenant(order.getTenantId(), order.getFeeId(),
                        BigDecimal.valueOf(order.getAmountInPaise()).movePointLeft(2),
                        "razorpay", orderId, paymentId);
                order.setStatus(PaymentOrderStatus.PAID);
                paymentOrderRepository.save(order);
            }
            case "payment.failed" -> {
                // An order can still be paid by a later retry, so only
                // CREATED -> FAILED; never downgrade a PAID order.
                if (order.getStatus() == PaymentOrderStatus.CREATED) {
                    order.setStatus(PaymentOrderStatus.FAILED);
                    paymentOrderRepository.save(order);
                }
            }
            default -> log.debug("Ignoring webhook event {}", event);
        }
    }

    private boolean validSignature(String body, String signature) {
        if (webhookSecret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
