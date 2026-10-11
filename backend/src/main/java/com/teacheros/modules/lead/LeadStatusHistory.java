package com.teacheros.modules.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead_status_history")
@Getter
@Setter
@NoArgsConstructor
public class LeadStatusHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private LeadStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private LeadStatus toStatus;

    @Column(length = 500)
    private String note;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        this.changedAt = Instant.now();
    }

    public LeadStatusHistory(UUID tenantId, UUID leadId, LeadStatus fromStatus, LeadStatus toStatus, String note,
            UUID changedByUserId) {
        this.tenantId = tenantId;
        this.leadId = leadId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedByUserId = changedByUserId;
    }
}
