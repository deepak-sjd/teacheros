package com.teacheros.modules.homework;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.homework.dto.HomeworkSubmissionDtos.ReviewSubmissionRequest;
import com.teacheros.modules.homework.dto.HomeworkSubmissionDtos.SubmissionResponse;
import com.teacheros.modules.material.AllowedContentTypes;
import com.teacheros.modules.material.dto.StudyMaterialDtos.DownloadUrlResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Student submissions for a homework assignment, and the teacher's review
 * of them.
 *
 * Access model (nothing here trusts a client-supplied tenant or student):
 * - submit(): STUDENT only; the student is resolved from the JWT via
 * StudentAccessGuard#resolveCurrentStudentId, never from the request.
 * - getForStudent()/getFileUrl(): StudentAccessGuard#assertCanView, so a
 * student sees only their own submission and a parent only their
 * child's. Another student's submission is a 404, not a 403.
 * - listForHomework()/review(): staff-only at the controller.
 *
 * Files are never served by a permanent link - only a short-lived signed
 * URL from StorageService, after the guard above has passed.
 */
@Service
public class HomeworkSubmissionService {

    // Same zone DoubtService/AnalyticsService use - the whole app assumes IST.
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int MAX_TEXT_LENGTH = 10_000;

    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkRepository homeworkRepository;
    private final StudentRepository studentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StorageService storageService;
    private final CurrentTenantProvider currentTenantProvider;
    private final long maxFileSizeBytes;

    public HomeworkSubmissionService(
            HomeworkSubmissionRepository submissionRepository,
            HomeworkRepository homeworkRepository,
            StudentRepository studentRepository,
            StudentAccessGuard studentAccessGuard,
            StorageService storageService,
            CurrentTenantProvider currentTenantProvider,
            @Value("${app.storage.max-file-size-mb}") long maxFileSizeMb) {
        this.submissionRepository = submissionRepository;
        this.homeworkRepository = homeworkRepository;
        this.studentRepository = studentRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.storageService = storageService;
        this.currentTenantProvider = currentTenantProvider;
        this.maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
    }

    // ---- Student: submit / resubmit ---------------------------------

    /**
     * Upsert by (homework, student). A resubmission replaces the text and,
     * if a new file is sent, the file; omitting the file keeps the existing
     * one. Resubmitting also resets any earlier review, since the feedback
     * referred to the previous content.
     */
    @Transactional
    public SubmissionResponse submit(UUID homeworkId, String text, MultipartFile file) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();

        Homework homework = requireHomework(tenantId, homeworkId);
        Student student = requireStudentInBatch(tenantId, studentId, homework);

        if (!isOpen(homework)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBMISSIONS_CLOSED",
                    "The due date for this homework has passed, so it can't be submitted or changed.");
        }

        String cleanText = blankToNull(text);
        if (cleanText != null && cleanText.length() > MAX_TEXT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEXT_TOO_LONG",
                    "Your answer is too long. The limit is " + MAX_TEXT_LENGTH + " characters.");
        }
        boolean hasNewFile = file != null && !file.isEmpty();
        if (hasNewFile) {
            validateFile(file);
        }

        HomeworkSubmission submission = submissionRepository
                .findByTenantIdAndHomeworkIdAndStudentId(tenantId, homeworkId, studentId)
                .orElseGet(() -> new HomeworkSubmission(tenantId, homeworkId, studentId));

        if (cleanText == null && !hasNewFile && submission.getObjectKey() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_SUBMISSION",
                    "Write an answer or attach a file before submitting.");
        }

        String oldKey = null;
        if (hasNewFile) {
            oldKey = submission.getObjectKey();
            submission.setObjectKey(storageService.upload(tenantId, homework.getBatchId(), file));
            submission.setOriginalFilename(file.getOriginalFilename());
            submission.setContentType(file.getContentType());
            submission.setFileSizeBytes(file.getSize());
        }
        submission.setText(cleanText);
        submission.setSubmittedAt(Instant.now());
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setTeacherFeedback(null);
        submission.setGrade(null);
        submission.setReviewedAt(null);
        submission.setReviewedByUserId(null);
        submission = submissionRepository.save(submission);

        // Delete the replaced file only after the row is saved - same
        // ordering reasoning as TenantService#uploadLogo.
        if (oldKey != null) {
            storageService.delete(oldKey);
        }

        return toResponse(submission, student.getFullName(), homework);
    }

    // ---- Student / parent / staff: read one student's submission -----

    public SubmissionResponse getForStudent(UUID homeworkId, UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        Homework homework = requireHomework(tenantId, homeworkId);
        Student student = requireStudentInBatch(tenantId, studentId, homework);

        return submissionRepository.findByTenantIdAndHomeworkIdAndStudentId(tenantId, homeworkId, studentId)
                .map(s -> toResponse(s, student.getFullName(), homework))
                .orElseGet(() -> notSubmitted(homework, student));
    }

    public DownloadUrlResponse getFileUrl(UUID submissionId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        HomeworkSubmission submission = requireSubmission(tenantId, submissionId);
        studentAccessGuard.assertCanView(submission.getStudentId());

        if (submission.getObjectKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_FILE", "This submission has no file attached.");
        }
        return new DownloadUrlResponse(storageService.generateDownloadUrl(submission.getObjectKey()), 15);
    }

    // ---- Staff: list and review -------------------------------------

    /**
     * Every active student in the homework's batch, with NOT_SUBMITTED rows
     * for those who haven't handed anything in - merged here rather than
     * left to the client, since the roster is already tenant-scoped.
     */
    public List<SubmissionResponse> listForHomework(UUID homeworkId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Homework homework = requireHomework(tenantId, homeworkId);

        List<Student> students = studentRepository.findByTenantIdAndBatchIdAndActiveTrue(
                tenantId, homework.getBatchId());
        Map<UUID, HomeworkSubmission> byStudent = submissionRepository
                .findByTenantIdAndHomeworkId(tenantId, homeworkId).stream()
                .collect(Collectors.toMap(HomeworkSubmission::getStudentId, Function.identity()));

        return students.stream()
                .sorted(Comparator.comparing(Student::getFullName))
                .map(s -> {
                    HomeworkSubmission sub = byStudent.get(s.getId());
                    return sub != null ? toResponse(sub, s.getFullName(), homework) : notSubmitted(homework, s);
                })
                .toList();
    }

    @Transactional
    public SubmissionResponse review(UUID submissionId, ReviewSubmissionRequest request) {
        String feedback = blankToNull(request.feedback());
        String grade = blankToNull(request.grade());
        if (feedback == null && grade == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_REVIEW",
                    "Add feedback or a grade before saving.");
        }

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        HomeworkSubmission submission = requireSubmission(tenantId, submissionId);
        Homework homework = requireHomework(tenantId, submission.getHomeworkId());
        UUID studentId = submission.getStudentId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        submission.setTeacherFeedback(feedback);
        submission.setGrade(grade);
        submission.setStatus(SubmissionStatus.REVIEWED);
        submission.setReviewedAt(Instant.now());
        submission.setReviewedByUserId(currentTenantProvider.getCurrentUserId());
        HomeworkSubmission saved = submissionRepository.save(submission);

        return toResponse(saved, student.getFullName(), homework);
    }

    // ---- helpers ------------------------------------------------------

    private Homework requireHomework(UUID tenantId, UUID homeworkId) {
        return homeworkRepository.findByIdAndTenantId(homeworkId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", homeworkId));
    }

    private HomeworkSubmission requireSubmission(UUID tenantId, UUID submissionId) {
        return submissionRepository.findByIdAndTenantId(submissionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
    }

    /**
     * A student can only interact with homework assigned to their own
     * batch. A mismatch is a 404 on the homework, not a 403, so it doesn't
     * confirm the assignment exists.
     */
    private Student requireStudentInBatch(UUID tenantId, UUID studentId, Homework homework) {
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        if (!homework.getBatchId().equals(student.getBatchId())) {
            throw new ResourceNotFoundException("Homework", homework.getId());
        }
        return student;
    }

    private boolean isOpen(Homework homework) {
        return !LocalDate.now(APP_ZONE).isAfter(homework.getDueDate());
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "That file is too large. Maximum size is " + (maxFileSizeBytes / (1024 * 1024)) + "MB.");
        }
        if (!AllowedContentTypes.isAllowed(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE",
                    "That file type isn't supported. Upload a PDF, Word document, image, or text file.");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private SubmissionResponse notSubmitted(Homework homework, Student student) {
        return new SubmissionResponse(null, homework.getId(), student.getId(), student.getFullName(),
                "NOT_SUBMITTED", null, null, null, null, null, null, null, isOpen(homework));
    }

    private SubmissionResponse toResponse(HomeworkSubmission s, String studentName, Homework homework) {
        return new SubmissionResponse(s.getId(), s.getHomeworkId(), s.getStudentId(), studentName,
                s.getStatus().name(), s.getText(), s.getOriginalFilename(), s.getFileSizeBytes(),
                s.getSubmittedAt(), s.getTeacherFeedback(), s.getGrade(), s.getReviewedAt(), isOpen(homework));
    }
}
