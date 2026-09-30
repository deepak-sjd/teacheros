package com.teacheros.modules.fee;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_payments")
@Getter
@Setter
@NoArgsConstructor
public class FeePayment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "fee_id", nullable = false)
    private UUID feeId;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    private String method;

    // Set only for online payments (method="razorpay") - null for
    // staff-recorded cash/manual entries. gatewayPaymentId has a unique
    // index so PaymentService can detect Razorpay retrying its callback
    // and avoid double-recording the same payment.
    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @PrePersist
    void onCreate() {
        if (this.paidAt == null) {
            this.paidAt = Instant.now();
        }
    }

    public FeePayment(UUID tenantId, UUID feeId, BigDecimal amountPaid, String method) {
        this.tenantId = tenantId;
        this.feeId = feeId;
        this.amountPaid = amountPaid;
        this.method = method;
    }
}
