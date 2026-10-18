package com.teacheros.modules.leave;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.attendance.Attendance;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.attendance.AttendanceStatus;
import com.teacheros.modules.attendance.ClassSession;
import com.teacheros.modules.attendance.ClassSessionRepository;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.DecideLeaveRequest;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.LeaveRequestResponse;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.SubmitLeaveRequest;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock LeaveRequestRepository leaveRequestRepository;
    @Mock StudentRepository studentRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock BatchRepository batchRepository;
    @Mock StudentAccessGuard studentAccessGuard;
    @Mock CurrentTenantProvider currentTenantProvider;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;

    LeaveRequestService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID batchId = UUID.randomUUID();
    final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    Student student;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(leaveRequestRepository, studentRepository, classSessionRepository,
                attendanceRepository, batchRepository, studentAccessGuard, currentTenantProvider, auditService,
                eventPublisher);

        student = new Student(tenantId, batchId, "Asha", "8", "+919000000001");
        student.setId(UUID.randomUUID());

        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(currentTenantProvider.getCurrentUserId()).thenReturn(userId);
        lenient().when(studentRepository.findByIdAndTenantId(student.getId(), tenantId))
                .thenReturn(Optional.of(student));
        lenient().when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> {
            LeaveRequest l = i.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            return l;
        });
    }

    // ---- submit ---------------------------------------------------------

    @Test
    void parentSubmitsAPendingRequest() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("PARENT");

        LeaveRequestResponse response = service.submit(student.getId(),
                new SubmitLeaveRequest(today.plusDays(1), today.plusDays(2), "  Fever  "));

        assertEquals(LeaveStatus.PENDING, response.status());
        assertEquals("Fever", response.reason());
        assertEquals("Asha", response.studentName());
    }

    @Test
    void staffCannotSubmitOnBehalfOfAFamily() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("TEACHER");

        ApiException ex = assertThrows(ApiException.class, () -> service.submit(student.getId(),
                new SubmitLeaveRequest(today, today, "x")));

        assertEquals("ONLY_FAMILY_CAN_REQUEST", ex.getCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void anotherFamilyIsStoppedByTheGuardBeforeAnythingElse() {
        doThrow(new ResourceNotFoundException("Student", student.getId()))
                .when(studentAccessGuard).assertCanView(student.getId());

        assertThrows(ResourceNotFoundException.class, () -> service.submit(student.getId(),
                new SubmitLeaveRequest(today, today, "x")));
        assertThrows(ResourceNotFoundException.class, () -> service.listForStudent(student.getId()));

        verifyNoInteractions(leaveRequestRepository);
    }

    @Test
    void endBeforeStartIsRejected() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("PARENT");

        ApiException ex = assertThrows(ApiException.class, () -> service.submit(student.getId(),
                new SubmitLeaveRequest(today.plusDays(3), today.plusDays(1), "x")));

        assertEquals("INVALID_RANGE", ex.getCode());
    }

    @Test
    void leaveOlderThanTheBackdateWindowIsRejected() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("PARENT");
        LocalDate tooOld = today.minusDays(LeaveRequestService.MAX_BACKDATE_DAYS + 1);

        ApiException ex = assertThrows(ApiException.class, () -> service.submit(student.getId(),
                new SubmitLeaveRequest(tooOld, today, "x")));

        assertEquals("LEAVE_TOO_OLD", ex.getCode());
    }

    @Test
    void yesterdayIsAllowedSoAnEarlierIllnessCanBeReported() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("PARENT");

        LeaveRequestResponse response = service.submit(student.getId(),
                new SubmitLeaveRequest(today.minusDays(1), today, "Was unwell"));

        assertEquals(LeaveStatus.PENDING, response.status());
    }

    @Test
    void aRangeLongerThanTheLimitIsRejected() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("PARENT");

        ApiException ex = assertThrows(ApiException.class, () -> service.submit(student.getId(),
                new SubmitLeaveRequest(today, today.plusDays(LeaveRequestService.MAX_RANGE_DAYS), "x")));

        assertEquals("LEAVE_TOO_LONG", ex.getCode());
    }

    @Test
    void overlappingARequestAlreadyPendingOrApprovedIsRejected() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("PARENT");
        when(leaveRequestRepository
                .existsByTenantIdAndStudentIdAndStatusInAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        eq(tenantId), eq(student.getId()), anyCollection(), any(), any()))
                .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.submit(student.getId(),
                new SubmitLeaveRequest(today, today.plusDays(1), "x")));

        assertEquals("LEAVE_OVERLAP", ex.getCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    // ---- decide ---------------------------------------------------------

    private LeaveRequest pending(LocalDate from, LocalDate to) {
        LeaveRequest l = new LeaveRequest(tenantId, student.getId(), from, to, "Fever, doctor advised rest", userId);
        l.setId(UUID.randomUUID());
        when(leaveRequestRepository.findForDecision(l.getId(), tenantId)).thenReturn(Optional.of(l));
        return l;
    }

    private ClassSession session(LocalDate date) {
        ClassSession s = new ClassSession(tenantId, batchId, date);
        s.setId(UUID.randomUUID());
        return s;
    }

    @Test
    void approvalExcusesAbsentButNeverOverwritesPresentOrLate() {
        LeaveRequest leave = pending(today, today.plusDays(3));
        ClassSession absentDay = session(today);
        ClassSession presentDay = session(today.plusDays(1));
        ClassSession lateDay = session(today.plusDays(2));
        ClassSession unmarkedDay = session(today.plusDays(3));
        when(classSessionRepository.findByTenantIdAndBatchIdAndSessionDateBetweenAndCancelledFalse(
                tenantId, batchId, leave.getFromDate(), leave.getToDate()))
                .thenReturn(List.of(absentDay, presentDay, lateDay, unmarkedDay));

        Attendance absent = new Attendance(tenantId, absentDay.getId(), student.getId(), AttendanceStatus.ABSENT, userId);
        Attendance present = new Attendance(tenantId, presentDay.getId(), student.getId(), AttendanceStatus.PRESENT, userId);
        Attendance late = new Attendance(tenantId, lateDay.getId(), student.getId(), AttendanceStatus.LATE, userId);
        when(attendanceRepository.findByTenantIdAndClassSessionIdAndStudentId(tenantId, absentDay.getId(), student.getId()))
                .thenReturn(Optional.of(absent));
        when(attendanceRepository.findByTenantIdAndClassSessionIdAndStudentId(tenantId, presentDay.getId(), student.getId()))
                .thenReturn(Optional.of(present));
        when(attendanceRepository.findByTenantIdAndClassSessionIdAndStudentId(tenantId, lateDay.getId(), student.getId()))
                .thenReturn(Optional.of(late));
        when(attendanceRepository.findByTenantIdAndClassSessionIdAndStudentId(tenantId, unmarkedDay.getId(), student.getId()))
                .thenReturn(Optional.empty());

        LeaveRequestResponse response = service.decide(leave.getId(), new DecideLeaveRequest("APPROVED", null));

        assertEquals(LeaveStatus.APPROVED, response.status());
        assertEquals(AttendanceStatus.EXCUSED, absent.getStatus());
        assertEquals(AttendanceStatus.PRESENT, present.getStatus());
        assertEquals(AttendanceStatus.LATE, late.getStatus());

        // The unmarked session gets a brand-new EXCUSED record.
        ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Attendance::getStatus)
                .containsOnly(AttendanceStatus.EXCUSED);
        assertThat(saved.getAllValues()).extracting(Attendance::getClassSessionId)
                .containsExactlyInAnyOrder(absentDay.getId(), unmarkedDay.getId());
    }

    @Test
    void approvalPublishesAnEventAndAuditsWithoutTheReasonText() {
        LeaveRequest leave = pending(today, today);
        when(classSessionRepository.findByTenantIdAndBatchIdAndSessionDateBetweenAndCancelledFalse(
                any(), any(), any(), any())).thenReturn(List.of());

        service.decide(leave.getId(), new DecideLeaveRequest("APPROVED", "Get well soon"));

        ArgumentCaptor<LeaveDecidedEvent> event = ArgumentCaptor.forClass(LeaveDecidedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().approved()).isTrue();
        assertThat(event.getValue().note()).isEqualTo("Get well soon");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(AuditActions.LEAVE_DECIDED), eq(AuditActions.LEAVE_REQUEST),
                eq(leave.getId()), meta.capture());
        assertThat(meta.getValue().values().toString()).doesNotContain("Fever");
        assertThat(meta.getValue()).doesNotContainKey("reason");
    }

    @Test
    void rejectionNeedsANote() {
        LeaveRequest leave = pending(today, today);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.decide(leave.getId(), new DecideLeaveRequest("REJECTED", "   ")));

        assertEquals("NOTE_REQUIRED", ex.getCode());
        assertEquals(LeaveStatus.PENDING, leave.getStatus());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectionTouchesNoAttendance() {
        LeaveRequest leave = pending(today, today);

        service.decide(leave.getId(), new DecideLeaveRequest("REJECTED", "Exam day"));

        assertEquals(LeaveStatus.REJECTED, leave.getStatus());
        verifyNoInteractions(classSessionRepository, attendanceRepository);
    }

    @Test
    void anAlreadyDecidedRequestCannotBeDecidedAgain() {
        LeaveRequest leave = pending(today, today);
        leave.setStatus(LeaveStatus.APPROVED);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.decide(leave.getId(), new DecideLeaveRequest("REJECTED", "changed my mind")));

        assertEquals("ALREADY_DECIDED", ex.getCode());
    }

    @Test
    void aRequestFromAnotherTenantIsNotFound() {
        UUID someoneElsesRequest = UUID.randomUUID();
        when(leaveRequestRepository.findForDecision(someoneElsesRequest, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(someoneElsesRequest, new DecideLeaveRequest("APPROVED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
