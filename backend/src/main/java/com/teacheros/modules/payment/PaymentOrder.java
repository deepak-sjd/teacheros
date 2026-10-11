package com.teacheros.modules.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "fee_id", nullable = false)
    private UUID feeId;

    @Column(name = "gateway_order_id", nullable = false)
    private String gatewayOrderId;

    @Column(name = "amount_in_paise", nullable = false)
    private long amountInPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderStatus status = PaymentOrderStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public PaymentOrder(UUID tenantId, UUID feeId, String gatewayOrderId, long amountInPaise) {
        this.tenantId = tenantId;
        this.feeId = feeId;
        this.gatewayOrderId = gatewayOrderId;
        this.amountInPaise = amountInPaise;
    }
}
