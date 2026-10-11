package com.teacheros.modules.lead;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.lead.dto.LeadDtos.AssignLeadRequest;
import com.teacheros.modules.lead.dto.LeadDtos.ConvertLeadRequest;
import com.teacheros.modules.lead.dto.LeadDtos.UpdateLeadStatusRequest;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    LeadRepository leadRepository;
    @Mock
    LeadStatusHistoryRepository historyRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    StudentRepository studentRepository;
    @Mock
    BatchRepository batchRepository;
    @Mock
    CurrentTenantProvider currentTenantProvider;
    @InjectMocks
    LeadService service;

    final UUID tenantA = UUID.randomUUID();
    final UUID tenantB = UUID.randomUUID();
    final UUID leadId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
        lenient().when(currentTenantProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());
    }

    private Lead newLead() {
        Lead lead = new Lead(tenantA, "Asha", "+919876543210", "asha@example.com", "Hi", LeadSource.WEBSITE);
        lead.setId(leadId);
        return lead;
    }

    @Test
    void get_leadOfAnotherTenant_isNotFound() {
        // The only lookup the service may do is tenant-scoped; B's lead is invisible to
        // A.
        when(leadRepository.findByIdAndTenantId(leadId, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(leadId));
        verify(leadRepository, never()).findById(any());
    }

    @Test
    void updateStatus_toEnrolled_mustGoThroughConvert() {
        when(leadRepository.findByIdAndTenantId(leadId, tenantA)).thenReturn(Optional.of(newLead()));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.updateStatus(leadId, new UpdateLeadStatusRequest(LeadStatus.ENROLLED, null)));
        assertEquals("USE_CONVERT", ex.getCode());
    }

    @Test
    void assign_userFromAnotherTenant_isRejected() {
        UUID outsider = UUID.randomUUID();
        User other = new User(tenantB, "+919000000001", "Outsider", Role.TEACHER);
        when(leadRepository.findByIdAndTenantId(leadId, tenantA)).thenReturn(Optional.of(newLead()));
        when(userRepository.findById(outsider)).thenReturn(Optional.of(other));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.assign(leadId, new AssignLeadRequest(outsider)));
        assertEquals("INVALID_ASSIGNEE", ex.getCode());
    }

    @Test
    void convert_createsStudentWithLeadPhoneAsGuardian_andClosesLead() {
        when(leadRepository.findByIdAndTenantId(leadId, tenantA)).thenReturn(Optional.of(newLead()));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.convert(leadId, new ConvertLeadRequest(null, "9", null));

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertEquals("+919876543210", captor.getValue().getGuardianPhone());
        assertEquals(tenantA, captor.getValue().getTenantId());
        assertEquals("Asha", captor.getValue().getFullName());
        assertEquals(LeadStatus.ENROLLED, result.lead().status());
    }
}
