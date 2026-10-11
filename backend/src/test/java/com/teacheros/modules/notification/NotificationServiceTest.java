package com.teacheros.modules.notification;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeRepository;
import com.teacheros.modules.notification.dto.NotificationDtos.GenerateResult;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;
    @Mock
    StudentRepository studentRepository;
    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    FeeRepository feeRepository;
    @Mock
    StudentAccessGuard studentAccessGuard;
    @Mock
    CurrentTenantProvider currentTenantProvider;
    @Mock
    SmsSender smsSender;
    @Mock
    EmailSender emailSender;

    NotificationService service;
    final UUID tenantId = UUID.randomUUID();
    Student student;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, studentRepository, attendanceRepository,
                feeRepository, studentAccessGuard, currentTenantProvider, smsSender, emailSender);
        student = new Student(tenantId, null, "Asha", "10", "+919000000001");
        student.setId(UUID.randomUUID());
    }

    @Test
    void scheduledFees_skipsFeesOutsideWindow_andClassifiesOverdue() {
        LocalDate today = LocalDate.of(2026, 10, 12);
        Fee dueSoon = new Fee(tenantId, student.getId(), BigDecimal.valueOf(500), today.plusDays(1));
        Fee farAway = new Fee(tenantId, student.getId(), BigDecimal.valueOf(500), today.plusDays(10));
        Fee late = new Fee(tenantId, student.getId(), BigDecimal.valueOf(500), today.minusDays(2));
        when(feeRepository.findAllByTenantIdAndStatusIn(eq(tenantId), any()))
                .thenReturn(List.of(dueSoon, farAway, late));
        when(studentRepository.findByIdAndTenantId(student.getId(), tenantId)).thenReturn(Optional.of(student));

        GenerateResult result = service.generateScheduledFeeRemindersForTenant(tenantId, today, 2);

        assertThat(result.created()).isEqualTo(2);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getType)
                .containsExactly(NotificationType.FEE_DUE, NotificationType.FEE_OVERDUE);
    }

    @Test
    void scheduledFees_studentInsideCooldownIsNotAlertedAgain() {
        LocalDate today = LocalDate.of(2026, 10, 12);
        Fee late = new Fee(tenantId, student.getId(), BigDecimal.valueOf(500), today.minusDays(2));
        when(feeRepository.findAllByTenantIdAndStatusIn(eq(tenantId), any())).thenReturn(List.of(late));
        when(notificationRepository.existsByTenantIdAndStudentIdAndTypeAndCreatedAtAfter(any(), any(), any(), any()))
                .thenReturn(true);

        GenerateResult result = service.generateScheduledFeeRemindersForTenant(tenantId, today, 2);

        assertThat(result.created()).isZero();
        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(smsSender);
    }

    @Test
    void absences_backdatedSessionIsIgnored() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

        int created = service.notifyAbsences(tenantId, yesterday, List.of(student.getId()));

        assertThat(created).isZero();
        verifyNoInteractions(notificationRepository, smsSender);
    }

    @Test
    void absences_todayAlertsGuardianOnce() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        when(studentRepository.findByIdAndTenantId(student.getId(), tenantId)).thenReturn(Optional.of(student));

        int created = service.notifyAbsences(tenantId, today, List.of(student.getId()));

        assertThat(created).isEqualTo(1);
        verify(notificationRepository).save(any(Notification.class));
        verify(smsSender).send(eq("+919000000001"), any());
    }

    @Test
    void absences_alreadyAlertedTodayIsSkipped() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        when(studentRepository.findByIdAndTenantId(student.getId(), tenantId)).thenReturn(Optional.of(student));
        when(notificationRepository.existsByTenantIdAndStudentIdAndTypeAndCreatedAtAfter(any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.notifyAbsences(tenantId, today, List.of(student.getId()))).isZero();
        verifyNoInteractions(smsSender);
    }
}
