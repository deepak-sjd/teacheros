package com.teacheros.modules.notification.dto;

import com.teacheros.modules.notification.NotificationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.time.Instant;
import java.util.UUID;

public class NotificationDtos {

    public record GenerateLowAttendanceRequest(
            @DecimalMin("0") @DecimalMax("100") double thresholdPercentage) {
    }

    public record GenerateResult(int created) {
    }

    public record NotificationResponse(
            UUID id,
            UUID studentId,
            NotificationType type,
            String title,
            String message,
            boolean read,
            Instant createdAt) {
    }
}
