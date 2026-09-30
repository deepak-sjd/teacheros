package com.teacheros.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentDtos {

    public record CreateOrderRequest(
            @NotNull UUID feeId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
    }

    /**
     * keyId is Razorpay's PUBLIC key - safe to expose to the frontend (it's
     * what Checkout.js needs to open the payment sheet). The secret key
     * never leaves the backend. Null in mock mode, where the frontend skips
     * Razorpay's checkout entirely and shows its own "Simulate payment"
     * button instead (branches on provider == "mock").
     */
    public record CreateOrderResponse(
            String provider,
            String orderId,
            long amountInPaise,
            String currency,
            String keyId,
            UUID feeId) {
    }

    public record VerifyPaymentRequest(
            @NotNull UUID feeId,
            @NotBlank String orderId,
            @NotBlank String paymentId,
            // Blank/omitted in mock mode - MockPaymentGateway#verifySignature
            // doesn't check it, there's nothing to forge since no real
            // money or real Razorpay order is involved.
            String signature) {
    }
}
