package com.teacheros.modules.fee;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fees")
@Getter
@Setter
@NoArgsConstructor
public class Fee {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // Always the NET amount owed (gross - discount). Existing payment/status
    // code keeps reading this column unchanged.
    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeeStatus status = FeeStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ---- V110 (fee structures). All nullable / defaulted. ----

    // Set only when the generating structure has exactly one head.
    @Column(name = "fee_head_id")
    private UUID feeHeadId;

    @Column(name = "structure_id")
    private UUID structureId;

    @Column(name = "instalment_no")
    private Integer instalmentNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private FeeLineType lineType = FeeLineType.STANDARD;

    // Null on legacy rows; treat as amountDue + discountAmount.
    @Column(name = "gross_amount", precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    // For LATE_FEE rows: the overdue instalment this charge belongs to.
    @Column(name = "parent_fee_id")
    private UUID parentFeeId;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Fee(UUID tenantId, UUID studentId, BigDecimal amountDue, LocalDate dueDate) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.amountDue = amountDue;
        this.dueDate = dueDate;
    }
}
