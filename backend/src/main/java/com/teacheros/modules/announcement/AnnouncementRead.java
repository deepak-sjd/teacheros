package com.teacheros.modules.announcement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Rows are inserted via AnnouncementReadRepository#markRead (idempotent
 * upsert), not by saving this entity.
 */
@Entity
@Table(name = "announcement_reads")
@Getter
@Setter
@NoArgsConstructor
public class AnnouncementRead {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "announcement_id", nullable = false)
    private UUID announcementId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;
}
