package com.teacheros.modules.notification;

import com.teacheros.modules.notification.dto.NotificationDtos.GenerateLowAttendanceRequest;
import com.teacheros.modules.notification.dto.NotificationDtos.GenerateResult;
import com.teacheros.modules.notification.dto.NotificationDtos.NotificationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/generate/low-attendance")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<GenerateResult> generateLowAttendance(
            @Valid @RequestBody GenerateLowAttendanceRequest request) {
        return ResponseEntity.ok(notificationService.generateLowAttendanceAlerts(request.thresholdPercentage()));
    }

    @PostMapping("/generate/fee-reminders")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<GenerateResult> generateFeeReminders() {
        return ResponseEntity.ok(notificationService.generateFeeReminders());
    }

    // No @PreAuthorize - open to PARENT/STUDENT, guarded inside the service.
    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<NotificationResponse>> listForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(notificationService.listForStudent(studentId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }
}
