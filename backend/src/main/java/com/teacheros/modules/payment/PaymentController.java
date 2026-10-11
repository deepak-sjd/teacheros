package com.teacheros.modules.payment;

import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderRequest;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.ReconciliationResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.VerifyPaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentWebhookService paymentWebhookService;
    private final PaymentReconciliationService paymentReconciliationService;

    public PaymentController(PaymentService paymentService, PaymentWebhookService paymentWebhookService,
            PaymentReconciliationService paymentReconciliationService) {
        this.paymentService = paymentService;
        this.paymentWebhookService = paymentWebhookService;
        this.paymentReconciliationService = paymentReconciliationService;
    }

    // No @PreAuthorize - PaymentService enforces "your own fee, or your own
    // child's fee" via StudentAccessGuard, same as FeeController's
    // /fees/students/{id}.
    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(paymentService.createOrder(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<FeeResponse> verify(@Valid @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verify(request));
    }

    // PUBLIC (permitAll in SecurityConfig) - no JWT. Authenticity is the
    // HMAC signature over the RAW body, so the body must be read as a
    // String, not bound to a DTO.
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentWebhookService.handle(body, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<ReconciliationResponse> reconciliation() {
        return ResponseEntity.ok(paymentReconciliationService.report());
    }
}
