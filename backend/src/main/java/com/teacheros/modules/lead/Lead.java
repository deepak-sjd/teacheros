package com.teacheros.modules.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "follow_up_reminded_for")
    private LocalDate followUpRemindedFor;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    private String email;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadSource source = LeadSource.WEBSITE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "next_follow_up")
    private LocalDate nextFollowUp;

    @Column(name = "converted_student_id")
    private UUID convertedStudentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Lead(UUID tenantId, String name, String phone, String email, String message, LeadSource source) {
        this.tenantId = tenantId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.message = message;
        this.source = source;
    }
}
