package com.teacheros.modules.feestructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fee_structure_items")
@Getter
@Setter
@NoArgsConstructor
public class FeeStructureItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "structure_id", nullable = false)
    private UUID structureId;

    @Column(name = "head_id", nullable = false)
    private UUID headId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    public FeeStructureItem(UUID tenantId, UUID structureId, UUID headId, BigDecimal amount) {
        this.tenantId = tenantId;
        this.structureId = structureId;
        this.headId = headId;
        this.amount = amount;
    }
}
