package com.teacheros.modules.homework;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.homework.dto.HomeworkSubmissionDtos.ReviewSubmissionRequest;
import com.teacheros.modules.homework.dto.HomeworkSubmissionDtos.SubmissionResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeworkSubmissionServiceTest {

    @Mock
    HomeworkSubmissionRepository submissionRepository;
    @Mock
    HomeworkRepository homeworkRepository;
    @Mock
    StudentRepository studentRepository;
    @Mock
    StudentAccessGuard studentAccessGuard;
    @Mock
    StorageService storageService;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    private HomeworkSubmissionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID homeworkId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HomeworkSubmissionService(submissionRepository, homeworkRepository, studentRepository,
                studentAccessGuard, storageService, currentTenantProvider, 20L);
        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(currentTenantProvider.getCurrentUserId()).thenReturn(userId);
        lenient().when(studentAccessGuard.resolveCurrentStudentId()).thenReturn(studentId);
        lenient().when(submissionRepository.save(any(HomeworkSubmission.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private Homework homework(LocalDate dueDate) {
        Homework h = new Homework(tenantId, batchId, "Chapter 3", null, dueDate, userId);
        h.setId(homeworkId);
        return h;
    }

    private Student student(UUID inBatch) {
        Student s = new Student(tenantId, inBatch, "Asha", "8", "+919000000001");
        s.setId(studentId);
        return s;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void submit_afterDueDate_isRejected() {
        when(homeworkRepository.findByIdAndTenantId(homeworkId, tenantId))
                .thenReturn(Optional.of(homework(today().minusDays(1))));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId))
                .thenReturn(Optional.of(student(batchId)));

        ApiException ex = assertThrows(ApiException.class, () -> service.submit(homeworkId, "my answer", null));

        assertEquals("SUBMISSIONS_CLOSED", ex.getCode());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submit_studentInAnotherBatch_getsNotFound() {
        when(homeworkRepository.findByIdAndTenantId(homeworkId, tenantId))
                .thenReturn(Optional.of(homework(today().plusDays(2))));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId))
                .thenReturn(Optional.of(student(UUID.randomUUID())));

        assertThrows(ResourceNotFoundException.class, () -> service.submit(homeworkId, "my answer", null));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submit_withNothing_isRejected() {
        when(homeworkRepository.findByIdAndTenantId(homeworkId, tenantId))
                .thenReturn(Optional.of(homework(today().plusDays(2))));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId))
                .thenReturn(Optional.of(student(batchId)));
        when(submissionRepository.findByTenantIdAndHomeworkIdAndStudentId(tenantId, homeworkId, studentId))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.submit(homeworkId, "   ", null));

        assertEquals("EMPTY_SUBMISSION", ex.getCode());
    }

    @Test
    void resubmit_withNewFile_replacesFileDeletesOldOneAndClearsReview() {
        HomeworkSubmission existing = new HomeworkSubmission(tenantId, homeworkId, studentId);
        existing.setObjectKey("old-key");
        existing.setStatus(SubmissionStatus.REVIEWED);
        existing.setTeacherFeedback("Good start");
        existing.setGrade("B");

        when(homeworkRepository.findByIdAndTenantId(homeworkId, tenantId))
                .thenReturn(Optional.of(homework(today().plusDays(2))));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId))
                .thenReturn(Optional.of(student(batchId)));
        when(submissionRepository.findByTenantIdAndHomeworkIdAndStudentId(tenantId, homeworkId, studentId))
                .thenReturn(Optional.of(existing));
        when(storageService.upload(any(), any(), any())).thenReturn("new-key");

        MockMultipartFile file = new MockMultipartFile("file", "answer.pdf", "application/pdf", new byte[] { 1, 2 });
        SubmissionResponse response = service.submit(homeworkId, "revised", file);

        verify(storageService).delete("old-key");
        assertEquals("SUBMITTED", response.status());
        assertEquals("answer.pdf", response.originalFilename());
        assertEquals("revised", response.text());
        assertNull(response.teacherFeedback());
        assertNull(response.grade());
    }

    @Test
    void review_withoutFeedbackOrGrade_isRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.review(UUID.randomUUID(), new ReviewSubmissionRequest("  ", null)));

        assertEquals("EMPTY_REVIEW", ex.getCode());
    }

    @Test
    void review_marksReviewedAndRecordsReviewer() {
        UUID submissionId = UUID.randomUUID();
        HomeworkSubmission existing = new HomeworkSubmission(tenantId, homeworkId, studentId);
        existing.setId(submissionId);

        when(submissionRepository.findByIdAndTenantId(submissionId, tenantId)).thenReturn(Optional.of(existing));
        when(homeworkRepository.findByIdAndTenantId(homeworkId, tenantId))
                .thenReturn(Optional.of(homework(today().plusDays(1))));
        when(studentRepository.findByIdAndTenantId(studentId, tenantId))
                .thenReturn(Optional.of(student(batchId)));

        SubmissionResponse response = service.review(submissionId, new ReviewSubmissionRequest("Well done", "A"));

        assertEquals("REVIEWED", response.status());
        assertEquals("Well done", response.teacherFeedback());
        assertEquals("A", response.grade());
        assertEquals(userId, existing.getReviewedByUserId());
    }
}
