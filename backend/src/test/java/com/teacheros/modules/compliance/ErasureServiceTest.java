package com.teacheros.modules.compliance;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErasureServiceTest {

    @Mock
    ErasureRequestRepository requests;
    @Mock
    StudentRepository students;
    @Mock
    StudentAccessGuard guard;
    @Mock
    CurrentTenantProvider current;
    @Mock
    AuditService audit;
    @Mock
    StudentErasureExecutor executor;
    @Mock
    PlatformTransactionManager txManager;

    ErasureService service;
    final UUID tenant = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ErasureService(requests, students, guard, current, audit, executor, txManager);
        org.mockito.Mockito.lenient().when(current.getCurrentTenantId()).thenReturn(tenant);
        org.mockito.Mockito.lenient().when(current.getCurrentUserId()).thenReturn(user);
        org.mockito.Mockito.lenient().when(students.findByIdAndTenantId(any(), any())).thenReturn(
                Optional.of(new Student(tenant, null, "Asha", "8", "+919876543210")));
        org.mockito.Mockito.lenient().when(requests.save(any(ErasureRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ErasureRequest pending() {
        return new ErasureRequest(tenant, studentId, user, "PARENT", "please");
    }

    @Test
    void studentAccountCannotRequestErasureOrSeeThatTheRecordExists() {
        when(current.getCurrentRole()).thenReturn("STUDENT");
        assertThatThrownBy(() -> service.request(studentId, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void anotherFamiliesChildIsA404ViaTheGuard() {
        when(current.getCurrentRole()).thenReturn("PARENT");
        doThrow(new ResourceNotFoundException("Student", studentId)).when(guard).assertCanView(studentId);
        assertThatThrownBy(() -> service.request(studentId, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void duplicateOpenRequestIs409() {
        when(current.getCurrentRole()).thenReturn("PARENT");
        when(requests.existsByTenantIdAndStudentIdAndStatusIn(eq(tenant), eq(studentId), anyCollection()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.request(studentId, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ERASURE_ALREADY_REQUESTED"));
    }

    @Test
    void approveRunsTheErasureAndCompletesTheRequest() {
        ErasureRequest r = pending();
        when(requests.findByIdAndTenantId(any(), eq(tenant))).thenReturn(Optional.of(r));
        when(executor.erase(tenant, studentId)).thenReturn(new StudentErasureExecutor.Result(2, 5, 0));

        service.approve(UUID.randomUUID(), "ok");

        assertThat(r.getStatus()).isEqualTo(ErasureStatus.COMPLETED);
        assertThat(r.getDecidedByUserId()).isEqualTo(user);
        assertThat(r.getCompletedAt()).isNotNull();
        verify(audit).record(eq(ComplianceAuditActions.ERASURE_COMPLETED), any(), any(), any());
    }

    @Test
    void failedErasureEndsAsFailedNotCompletedAndCanBeRetried() {
        ErasureRequest r = pending();
        when(requests.findByIdAndTenantId(any(), eq(tenant))).thenReturn(Optional.of(r));
        when(executor.erase(tenant, studentId)).thenThrow(new IllegalStateException("storage down"));

        service.approve(UUID.randomUUID(), null);

        assertThat(r.getStatus()).isEqualTo(ErasureStatus.FAILED);
        assertThat(r.getFailureReason()).doesNotContain("storage down"); // no internals leaked to the client
        verify(audit).record(eq(ComplianceAuditActions.ERASURE_FAILED), any(), any(), any());
    }

    @Test
    void completedRequestCannotBeApprovedAgain() {
        ErasureRequest r = pending();
        r.setStatus(ErasureStatus.COMPLETED);
        when(requests.findByIdAndTenantId(any(), eq(tenant))).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_PENDING"));
        verify(executor, never()).erase(any(), any());
    }

    @Test
    void rejectOnlyWorksOnPending() {
        ErasureRequest r = pending();
        when(requests.findByIdAndTenantId(any(), eq(tenant))).thenReturn(Optional.of(r));
        service.reject(UUID.randomUUID(), "not verified");
        assertThat(r.getStatus()).isEqualTo(ErasureStatus.REJECTED);
        verify(executor, never()).erase(any(), any());
    }
}
