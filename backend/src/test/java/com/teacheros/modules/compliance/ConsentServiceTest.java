package com.teacheros.modules.compliance;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.compliance.dto.ComplianceDtos.ConsentStatusResponse;
import com.teacheros.modules.compliance.dto.ComplianceDtos.GrantConsentRequest;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import com.teacheros.modules.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    ConsentRecordRepository consents;
    @Mock
    StudentRepository students;
    @Mock
    UserRepository users;
    @Mock
    StudentAccessGuard guard;
    @Mock
    CurrentTenantProvider current;
    @Mock
    AuditService audit;

    ConsentService service;
    final UUID tenant = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final String phone = "+919876543210";
    Student child;

    @BeforeEach
    void setUp() {
        service = new ConsentService(consents, students, users, guard, current, audit, "v1");
        child = new Student(tenant, null, "Asha", "8", phone);
        child.setId(UUID.randomUUID());
        org.mockito.Mockito.lenient().when(current.getCurrentTenantId()).thenReturn(tenant);
        org.mockito.Mockito.lenient().when(current.getCurrentUserId()).thenReturn(userId);
        org.mockito.Mockito.lenient().when(current.getCurrentRole()).thenReturn("PARENT");
        org.mockito.Mockito.lenient().when(users.findById(userId))
                .thenReturn(Optional.of(new User(tenant, phone, "Parent", Role.PARENT)));
        org.mockito.Mockito.lenient().when(students.findByTenantIdAndGuardianPhoneAndActiveTrue(tenant, phone))
                .thenReturn(List.of(child));
    }

    @Test
    void firstLoginRequiresConsentUntilGranted() {
        when(consents.findByTenantIdAndStudentIdIn(any(), any())).thenReturn(List.of());
        ConsentStatusResponse status = service.status();
        assertThat(status.consentRequired()).isTrue();
        assertThat(status.children().get(0).state()).isEqualTo("PENDING");
    }

    @Test
    void grantRecordsConsentForOwnChildrenOnly() {
        when(consents.findByTenantIdAndStudentIdIn(any(), any())).thenReturn(List.of());
        service.grant(new GrantConsentRequest("v1", null));
        verify(consents).save(org.mockito.ArgumentMatchers.argThat(
                (ConsentRecord r) -> r.getStudentId().equals(child.getId()) && r.getGuardianPhone().equals(phone)));
    }

    @Test
    void grantingForSomeoneElsesChildIs404AndSavesNothing() {
        assertThatThrownBy(() -> service.grant(new GrantConsentRequest("v1", List.of(UUID.randomUUID()))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(consents, never()).save(any());
    }

    @Test
    void withdrawnChildDoesNotBlockTheAppAgain() {
        ConsentRecord r = new ConsentRecord(tenant, child.getId(), phone, "CHILD_DATA_PROCESSING", "v1");
        r.setGrantedAt(java.time.Instant.now());
        r.setWithdrawnAt(java.time.Instant.now());
        when(consents.findByTenantIdAndStudentIdIn(any(), any())).thenReturn(List.of(r));
        ConsentStatusResponse status = service.status();
        assertThat(status.children().get(0).state()).isEqualTo("WITHDRAWN");
        assertThat(status.consentRequired()).isFalse();
    }
}
