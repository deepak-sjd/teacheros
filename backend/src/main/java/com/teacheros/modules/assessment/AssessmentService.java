package com.teacheros.modules.assessment;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.dto.AssessmentDtos.AssessmentResponse;
import com.teacheros.modules.assessment.dto.AssessmentDtos.CreateAssessmentRequest;
import com.teacheros.modules.assessment.dto.AssessmentDtos.UpdateAssessmentRequest;
import com.teacheros.modules.assessment.dto.MarkDtos.MarkResponse;
import com.teacheros.modules.assessment.dto.MarkDtos.StudentMarkEntry;
import com.teacheros.modules.assessment.dto.MarkDtos.StudentMarkHistoryEntry;
import com.teacheros.modules.assessment.dto.MarkDtos.SubmitMarksRequest;
import com.teacheros.modules.batch.BatchAccessGuard;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final MarkRepository markRepository;
    private final BatchRepository batchRepository;
    private final BatchAccessGuard batchAccessGuard;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            MarkRepository markRepository,
            BatchRepository batchRepository,
            BatchAccessGuard batchAccessGuard,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.assessmentRepository = assessmentRepository;
        this.markRepository = markRepository;
        this.batchRepository = batchRepository;
        this.batchAccessGuard = batchAccessGuard;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public AssessmentResponse create(CreateAssessmentRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));

        Assessment assessment = new Assessment(tenantId, request.batchId(), request.title(), request.subject(),
                request.maxMarks(), request.testDate(), userId);
        return toResponse(assessmentRepository.save(assessment));
    }

    @Transactional
    public AssessmentResponse update(UUID testId, UpdateAssessmentRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = assessmentRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", testId));

        assessment.setTitle(request.title());
        assessment.setSubject(request.subject());
        assessment.setMaxMarks(request.maxMarks());
        assessment.setTestDate(request.testDate());
        return toResponse(assessmentRepository.save(assessment));
    }

    @Transactional
    public void delete(UUID testId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = assessmentRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", testId));
        // Marks reference this test by ID (not a JPA relationship/cascade),
        // so they're cleaned up explicitly rather than relying on a
        // cascade the entity mapping doesn't declare.
        markRepository.deleteAll(markRepository.findByTenantIdAndTestId(tenantId, testId));
        assessmentRepository.delete(assessment);
    }

    public AssessmentResponse get(UUID testId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = assessmentRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", testId));
        batchAccessGuard.assertCanView(assessment.getBatchId());
        return toResponse(assessment);
    }

    public Page<AssessmentResponse> listForBatch(UUID batchId, Pageable pageable) {
        batchAccessGuard.assertCanView(batchId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return assessmentRepository.findByTenantIdAndBatchIdOrderByTestDateDesc(tenantId, batchId, pageable)
                .map(this::toResponse);
    }

    /**
     * Staff-only bulk upsert - grading a whole class in one submission,
     * same pattern as AttendanceService#markAttendance. Every entry is
     * validated against THIS test's maxMarks before anything is saved, so
     * a typo doesn't half-save a batch of marks.
     */
    @Transactional
    public List<MarkResponse> submitMarks(UUID testId, SubmitMarksRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = assessmentRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", testId));

        for (StudentMarkEntry entry : request.entries()) {
            if (entry.marksObtained() != null) {
                if (entry.marksObtained().compareTo(BigDecimal.ZERO) < 0
                        || entry.marksObtained().compareTo(assessment.getMaxMarks()) > 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "MARKS_OUT_OF_RANGE",
                            "Marks for " + entry.studentId() + " must be between 0 and " + assessment.getMaxMarks());
                }
            }
        }

        return request.entries().stream()
                .map(entry -> upsertMark(tenantId, testId, entry))
                .map(m -> new MarkResponse(m.getStudentId(), m.getMarksObtained()))
                .toList();
    }

    private Mark upsertMark(UUID tenantId, UUID testId, StudentMarkEntry entry) {
        Mark mark = markRepository.findByTenantIdAndTestIdAndStudentId(tenantId, testId, entry.studentId())
                .orElseGet(() -> new Mark(tenantId, testId, entry.studentId(), entry.marksObtained()));
        mark.setMarksObtained(entry.marksObtained());
        return markRepository.save(mark);
    }

    /**
     * Staff-only roster view for grading/review - every student's mark for
     * one test. Deliberately NOT exposed to PARENT/STUDENT, even via
     * BatchAccessGuard - seeing classmates' marks is not something batch
     * membership should grant, unlike homework/study material.
     */
    public List<MarkResponse> listMarksForTest(UUID testId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        assessmentRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", testId));
        return markRepository.findByTenantIdAndTestId(tenantId, testId).stream()
                .map(m -> new MarkResponse(m.getStudentId(), m.getMarksObtained()))
                .toList();
    }

    /**
     * A single student's marks across every test - the report-card data.
     * Guarded per-student (not per-batch) since this is exactly the kind
     * of "only your own child" check StudentAccessGuard exists for.
     */
    public List<StudentMarkHistoryEntry> getStudentMarkHistory(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        return markRepository.findByTenantIdAndStudentIdOrderByTestDateDesc(tenantId, studentId).stream()
                .map(mark -> {
                    Assessment assessment = assessmentRepository.findByIdAndTenantId(mark.getTestId(), tenantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Test", mark.getTestId()));
                    return new StudentMarkHistoryEntry(assessment.getId(), assessment.getTitle(),
                            assessment.getSubject(), assessment.getMaxMarks(), mark.getMarksObtained(),
                            assessment.getTestDate());
                })
                .toList();
    }

    private AssessmentResponse toResponse(Assessment assessment) {
        return new AssessmentResponse(assessment.getId(), assessment.getBatchId(), assessment.getTitle(),
                assessment.getSubject(), assessment.getMaxMarks(), assessment.getTestDate(),
                assessment.getCreatedAt(), assessment.getUpdatedAt());
    }
}
