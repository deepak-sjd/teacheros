package com.teacheros.modules.attendance;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.attendance.dto.AttendanceDtos.MarkAttendanceRequest;
import com.teacheros.modules.attendance.dto.AttendanceDtos.StudentAttendanceEntry;
import com.teacheros.modules.attendance.dto.AttendanceDtos.StudentAttendanceRecord;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.leave.LeaveRequestRepository;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The "sessions that will be created later" half of the leave rule: when a
 * teacher marks a day covered by an APPROVED leave, ABSENT becomes EXCUSED -
 * but PRESENT and LATE entered by the teacher are never touched, and an
 * excused student doesn't trigger a same-day absent alert.
 */
@ExtendWith(MockitoExtension.class)
class LeaveExcusedOnMarkTest {

    @Mock AttendanceRepository attendanceRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock BatchRepository batchRepository;
    @Mock CurrentTenantProvider currentTenantProvider;
    @Mock StudentAccessGuard studentAccessGuard;
    @Mock AttendanceQrTokenService qrTokenService;
    @Mock StudentRepository studentRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock LeaveRequestRepository leaveRequestRepository;

    AttendanceService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID batchId = UUID.randomUUID();
    final LocalDate date = LocalDate.of(2026, 3, 10);
    final UUID onLeave = UUID.randomUUID();
    final UUID absentNoLeave = UUID.randomUUID();
    final UUID presentOnLeave = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendanceRepository, classSessionRepository, batchRepository,
                currentTenantProvider, studentAccessGuard, qrTokenService, studentRepository, userRepository,
                eventPublisher, leaveRequestRepository);

        ClassSession session = new ClassSession(tenantId, batchId, date);
        session.setId(UUID.randomUUID());

        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(currentTenantProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());
        lenient().when(batchRepository.findByIdAndTenantId(batchId, tenantId))
                .thenReturn(Optional.of(new Batch(tenantId, "Batch", "Maths", null)));
        lenient().when(classSessionRepository.findByTenantIdAndBatchIdAndSessionDate(tenantId, batchId, date))
                .thenReturn(Optional.of(session));
        lenient().when(attendanceRepository.findByTenantIdAndClassSessionIdAndStudentId(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(attendanceRepository.save(any(Attendance.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void absentBecomesExcusedOnlyForAStudentOnApprovedLeave() {
        when(leaveRequestRepository.findApprovedStudentIdsCovering(any(), any(), any()))
                .thenReturn(List.of(onLeave, presentOnLeave));

        List<StudentAttendanceRecord> result = service.markAttendance(new MarkAttendanceRequest(batchId, date, List.of(
                new StudentAttendanceEntry(onLeave, AttendanceStatus.ABSENT),
                new StudentAttendanceEntry(absentNoLeave, AttendanceStatus.ABSENT),
                new StudentAttendanceEntry(presentOnLeave, AttendanceStatus.PRESENT))));

        assertThat(result).filteredOn(r -> r.studentId().equals(onLeave))
                .singleElement().extracting(StudentAttendanceRecord::status).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(result).filteredOn(r -> r.studentId().equals(absentNoLeave))
                .singleElement().extracting(StudentAttendanceRecord::status).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(result).filteredOn(r -> r.studentId().equals(presentOnLeave))
                .singleElement().extracting(StudentAttendanceRecord::status).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void anExcusedStudentIsNotInTheAbsentAlertList() {
        when(leaveRequestRepository.findApprovedStudentIdsCovering(any(), any(), any())).thenReturn(List.of(onLeave));

        service.markAttendance(new MarkAttendanceRequest(batchId, date, List.of(
                new StudentAttendanceEntry(onLeave, AttendanceStatus.ABSENT),
                new StudentAttendanceEntry(absentNoLeave, AttendanceStatus.ABSENT))));

        ArgumentCaptor<AttendanceMarkedEvent> event = ArgumentCaptor.forClass(AttendanceMarkedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().absentStudentIds()).containsExactly(absentNoLeave);
    }

    @Test
    void withNoApprovedLeaveBehaviourIsUnchanged() {
        when(leaveRequestRepository.findApprovedStudentIdsCovering(any(), any(), any())).thenReturn(List.of());

        List<StudentAttendanceRecord> result = service.markAttendance(new MarkAttendanceRequest(batchId, date, List.of(
                new StudentAttendanceEntry(onLeave, AttendanceStatus.ABSENT))));

        assertThat(result).singleElement().extracting(StudentAttendanceRecord::status)
                .isEqualTo(AttendanceStatus.ABSENT);
    }
}
