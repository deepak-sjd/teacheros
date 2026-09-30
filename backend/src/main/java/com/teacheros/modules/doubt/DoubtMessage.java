package com.teacheros.modules.doubt;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doubt_messages")
@Getter
@Setter
@NoArgsConstructor
public class DoubtMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoubtSender sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public DoubtMessage(UUID tenantId, UUID studentId, DoubtSender sender, String body) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.sender = sender;
        this.body = body;
    }
}
