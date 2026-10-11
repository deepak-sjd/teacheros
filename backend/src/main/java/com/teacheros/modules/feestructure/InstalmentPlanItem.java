package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "instalment_plan_items")
@Getter
@Setter
@NoArgsConstructor
public class InstalmentPlanItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "instalment_no", nullable = false)
    private int instalmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    public InstalmentPlanItem(UUID tenantId, UUID planId, int instalmentNo, LocalDate dueDate,
            BigDecimal percentage) {
        this.tenantId = tenantId;
        this.planId = planId;
        this.instalmentNo = instalmentNo;
        this.dueDate = dueDate;
        this.percentage = percentage;
    }
}
