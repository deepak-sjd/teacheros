package com.teacheros.modules.notification;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.fee.FeeRepository;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveDecisionNotificationTest {

    @Mock NotificationRepository notificationRepository;
    @Mock StudentRepository studentRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock FeeRepository feeRepository;
    @Mock StudentAccessGuard studentAccessGuard;
    @Mock CurrentTenantProvider currentTenantProvider;
    @Mock SmsSender smsSender;
    @Mock EmailSender emailSender;

    NotificationService service;
    final UUID tenantId = UUID.randomUUID();
    Student student;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, studentRepository, attendanceRepository,
                feeRepository, studentAccessGuard, currentTenantProvider, smsSender, emailSender);
        student = new Student(tenantId, null, "Asha", "8", "+919000000001");
        student.setId(UUID.randomUUID());
    }

    @Test
    void approvedLeaveCreatesAnInAppNotificationAndTextsTheGuardian() {
        when(studentRepository.findByIdAndTenantId(student.getId(), tenantId)).thenReturn(Optional.of(student));

        service.notifyLeaveDecision(tenantId, student.getId(), true,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "Get well soon");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.LEAVE_DECISION);
        assertThat(saved.getValue().getTitle()).isEqualTo("Leave approved");
        assertThat(saved.getValue().getMessage()).contains("Asha", "10 Mar 2026 to 12 Mar 2026", "approved", "Get well soon");
        verify(smsSender).send(eq("+919000000001"), any());
    }

    @Test
    void rejectedLeaveSaysSoAndNeverExceedsTheColumnLength() {
        when(studentRepository.findByIdAndTenantId(student.getId(), tenantId)).thenReturn(Optional.of(student));

        service.notifyLeaveDecision(tenantId, student.getId(), false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), "x".repeat(500));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Leave not approved");
        assertThat(saved.getValue().getMessage()).hasSizeLessThanOrEqualTo(500);
    }

    @Test
    void aStudentFromAnotherTenantIsIgnored() {
        when(studentRepository.findByIdAndTenantId(student.getId(), tenantId)).thenReturn(Optional.empty());

        service.notifyLeaveDecision(tenantId, student.getId(), true, LocalDate.now(), LocalDate.now(), null);

        verifyNoInteractions(notificationRepository, smsSender, emailSender);
    }
}
