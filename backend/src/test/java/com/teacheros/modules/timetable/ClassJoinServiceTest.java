package com.teacheros.modules.timetable;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.timetable.dto.ClassJoinDtos.JoinResponse;
import com.teacheros.modules.timetable.dto.ClassJoinDtos.SessionJoinsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassJoinServiceTest {

    @Mock
    ClassJoinLogRepository joinLogRepository;
    @Mock
    TimetableSlotRepository timetableSlotRepository;
    @Mock
    StudentRepository studentRepository;
    @Mock
    StudentAccessGuard studentAccessGuard;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    private ClassJoinService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClassJoinService(joinLogRepository, timetableSlotRepository, studentRepository,
                studentAccessGuard, currentTenantProvider);
        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(studentAccessGuard.resolveCurrentStudentId()).thenReturn(studentId);
        lenient().when(joinLogRepository.save(any(ClassJoinLog.class))).thenAnswer(i -> {
            ClassJoinLog log = i.getArgument(0);
            log.onCreate();
            return log;
        });
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    private TimetableSlot slot(int dayOfWeek) {
        TimetableSlot s = new TimetableSlot(tenantId, batchId, dayOfWeek, LocalTime.of(16, 0), LocalTime.of(17, 0),
                "Maths");
        s.setId(slotId);
        return s;
    }

    private Student student(UUID inBatch) {
        Student s = new Student(tenantId, inBatch, "Asha", "8", "+919000000001");
        s.setId(studentId);
        return s;
    }

    @Test
    void join_onTheSlotsDay_recordsFirstJoin() {
        when(timetableSlotRepository.findByIdAndTenantId(slotId, tenantId))
                .thenReturn(Optional.of(slot(today().getDayOfWeek().getValue())));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId)).thenReturn(Optional.of(student(batchId)));
        when(joinLogRepository.findByTenantIdAndTimetableSlotIdAndStudentIdAndSessionDate(
                tenantId, slotId, studentId, today())).thenReturn(Optional.empty());

        JoinResponse response = service.recordJoin(slotId);

        assertTrue(response.recorded());
        assertNotNull(response.joinedAt());
        verify(joinLogRepository).save(any(ClassJoinLog.class));
    }

    @Test
    void join_again_keepsFirstJoinAndDoesNotInsert() {
        ClassJoinLog first = new ClassJoinLog(tenantId, slotId, studentId, today());
        first.onCreate();

        when(timetableSlotRepository.findByIdAndTenantId(slotId, tenantId))
                .thenReturn(Optional.of(slot(today().getDayOfWeek().getValue())));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId)).thenReturn(Optional.of(student(batchId)));
        when(joinLogRepository.findByTenantIdAndTimetableSlotIdAndStudentIdAndSessionDate(
                tenantId, slotId, studentId, today())).thenReturn(Optional.of(first));

        JoinResponse response = service.recordJoin(slotId);

        assertTrue(response.recorded());
        assertEquals(first.getJoinedAt(), response.joinedAt());
        verify(joinLogRepository, never()).save(any());
    }

    @Test
    void join_onAnotherDay_isNotRecorded() {
        int otherDay = today().getDayOfWeek().getValue() % 7 + 1;
        when(timetableSlotRepository.findByIdAndTenantId(slotId, tenantId)).thenReturn(Optional.of(slot(otherDay)));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId)).thenReturn(Optional.of(student(batchId)));

        JoinResponse response = service.recordJoin(slotId);

        assertFalse(response.recorded());
        assertNull(response.joinedAt());
        verify(joinLogRepository, never()).save(any());
    }

    @Test
    void join_slotOfAnotherBatch_getsNotFound() {
        when(timetableSlotRepository.findByIdAndTenantId(slotId, tenantId))
                .thenReturn(Optional.of(slot(today().getDayOfWeek().getValue())));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId))
                .thenReturn(Optional.of(student(UUID.randomUUID())));

        assertThrows(ResourceNotFoundException.class, () -> service.recordJoin(slotId));
        verify(joinLogRepository, never()).save(any());
    }

    @Test
    void listJoins_returnsNamesRosterSizeAndRespectsDate() {
        LocalDate date = today();
        ClassJoinLog log = new ClassJoinLog(tenantId, slotId, studentId, date);
        log.onCreate();

        when(timetableSlotRepository.findByIdAndTenantId(slotId, tenantId)).thenReturn(Optional.of(slot(1)));
        when(studentRepository.findByTenantIdAndBatchIdAndActiveTrue(tenantId, batchId))
                .thenReturn(List.of(student(batchId)));
        when(joinLogRepository.findByTenantIdAndTimetableSlotIdAndSessionDateOrderByJoinedAtAsc(
                tenantId, slotId, date)).thenReturn(List.of(log));

        SessionJoinsResponse response = service.listJoins(slotId, date);

        assertEquals(date, response.sessionDate());
        assertEquals(1, response.rosterSize());
        assertEquals(1, response.joined().size());
        assertEquals("Asha", response.joined().get(0).studentName());
    }
}
