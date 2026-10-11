package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fee_line_items")
@Getter
@Setter
@NoArgsConstructor
public class FeeLineItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "fee_id", nullable = false)
    private UUID feeId;

    @Column(name = "head_id", nullable = false)
    private UUID headId;

    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    public FeeLineItem(UUID tenantId, UUID feeId, UUID headId, BigDecimal grossAmount, BigDecimal discountAmount) {
        this.tenantId = tenantId;
        this.feeId = feeId;
        this.headId = headId;
        this.grossAmount = grossAmount;
        this.discountAmount = discountAmount;
    }

    public BigDecimal netAmount() {
        return grossAmount.subtract(discountAmount);
    }
}
