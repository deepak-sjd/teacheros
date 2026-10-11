package com.teacheros.modules.student;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.student.dto.StudentProfileDtos.StudentProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the access/visibility rules. The tenant-isolation
 * integration test for student_documents (Testcontainers) is separate and
 * needs the project's existing integration-test base class.
 */
@ExtendWith(MockitoExtension.class)
class StudentProfileServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    StudentDocumentRepository studentDocumentRepository;
    @Mock
    StudentAccessGuard studentAccessGuard;
    @Mock
    StorageService storageService;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    StudentProfileService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID studentId = UUID.randomUUID();
    Student student;

    @BeforeEach
    void setUp() {
        service = new StudentProfileService(studentRepository, studentDocumentRepository, studentAccessGuard,
                storageService, currentTenantProvider, 20);

        student = new Student(tenantId, null, "Asha", "8", "+919000000001");
        student.setId(studentId);
        student.setMedicalNotes("Asthma - carries inhaler");
        student.setBloodGroup("O+");

        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(currentTenantProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());
        lenient().when(studentRepository.findByIdAndTenantId(studentId, tenantId)).thenReturn(Optional.of(student));
    }

    @Test
    void staffSeeMedicalNotes() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("TEACHER");

        StudentProfileResponse response = service.getProfile(studentId);

        assertTrue(response.medicalNotesVisible());
        assertEquals("Asthma - carries inhaler", response.medicalNotes());
    }

    @Test
    void parentSeesOwnChildsMedicalNotes() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("PARENT");

        StudentProfileResponse response = service.getProfile(studentId);

        assertTrue(response.medicalNotesVisible());
        assertEquals("Asthma - carries inhaler", response.medicalNotes());
    }

    @Test
    void studentRoleDoesNotSeeMedicalNotes() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("STUDENT");

        StudentProfileResponse response = service.getProfile(studentId);

        assertFalse(response.medicalNotesVisible());
        assertNull(response.medicalNotes());
        assertEquals("O+", response.bloodGroup());
    }

    @Test
    void otherParentIsRejectedByGuardBeforeAnythingIsRead() {
        doThrow(new ResourceNotFoundException("Student", studentId)).when(studentAccessGuard).assertCanView(studentId);

        assertThrows(ResourceNotFoundException.class, () -> service.getProfile(studentId));
        assertThrows(ResourceNotFoundException.class, () -> service.listDocuments(studentId));

        verifyNoInteractions(studentDocumentRepository);
        verify(studentRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void profileResponseToStringDoesNotLeakMedicalNotes() {
        when(currentTenantProvider.getCurrentRole()).thenReturn("OWNER");

        String text = service.getProfile(studentId).toString();

        assertFalse(text.contains("Asthma"));
    }

    @Test
    void uploadRejectsUnsupportedFileType() {
        MockMultipartFile word = new MockMultipartFile("file", "x.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] { 1 });

        ApiException ex = assertThrows(ApiException.class,
                () -> service.uploadDocument(studentId, "ID_PROOF", "Aadhaar", word));

        assertEquals("UNSUPPORTED_FILE_TYPE", ex.getCode());
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadRejectsUnknownDocumentType() {
        MockMultipartFile pdf = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[] { 1 });

        ApiException ex = assertThrows(ApiException.class,
                () -> service.uploadDocument(studentId, "NOT_A_TYPE", "Doc", pdf));

        assertEquals("INVALID_DOCUMENT_TYPE", ex.getCode());
    }

    @Test
    void documentFromAnotherStudentIsNotFound() {
        UUID documentId = UUID.randomUUID();
        when(studentDocumentRepository.findByIdAndTenantIdAndStudentId(documentId, tenantId, studentId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDownloadUrl(studentId, documentId));
        verifyNoInteractions(storageService);
    }
}
