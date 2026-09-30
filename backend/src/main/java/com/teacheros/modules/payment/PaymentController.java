package com.teacheros.modules.payment;

import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderRequest;
import com.teacheros.modules.payment.dto.PaymentDtos.CreateOrderResponse;
import com.teacheros.modules.payment.dto.PaymentDtos.VerifyPaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
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
}
