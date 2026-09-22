package com.teacheros.modules.message;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    private SenderRole senderRole;

    // Denormalized display name at send time (a User's displayName, or
    // "Parent" as a fallback) - kept on the message itself so the thread
    // still reads sensibly even if the sender's account is later renamed
    // or deactivated.
    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "read_by_parent", nullable = false)
    private boolean readByParent;

    @Column(name = "read_by_staff", nullable = false)
    private boolean readByStaff;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Message(UUID tenantId, UUID studentId, SenderRole senderRole, String senderName, String body) {
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.senderRole = senderRole;
        this.senderName = senderName;
        this.body = body;
        // A message is obviously already "read" by whichever side sent
        // it - only the other side starts unread.
        this.readByParent = senderRole == SenderRole.PARENT;
        this.readByStaff = senderRole == SenderRole.STAFF;
    }
}
