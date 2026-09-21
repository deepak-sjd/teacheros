package com.teacheros.modules.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Notification> findByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId);

    // Dedupe check - don't re-alert the same student for the same reason
    // within the cooldown window (see NotificationService.COOLDOWN).
    boolean existsByTenantIdAndStudentIdAndTypeAndCreatedAtAfter(
            UUID tenantId, UUID studentId, NotificationType type, Instant after);
}
