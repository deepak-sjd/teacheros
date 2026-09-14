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

    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeeStatus status = FeeStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
