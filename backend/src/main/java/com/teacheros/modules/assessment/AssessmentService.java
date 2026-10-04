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
import com.teacheros.modules.assessment.dto.RankingDtos.BatchRankingEntry;
import com.teacheros.modules.assessment.dto.RankingDtos.BatchRankingResponse;
import com.teacheros.modules.assessment.dto.RankingDtos.StudentRankResponse;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchAccessGuard;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final MarkRepository markRepository;
    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final BatchAccessGuard batchAccessGuard;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            MarkRepository markRepository,
            BatchRepository batchRepository,
            StudentRepository studentRepository,
            BatchAccessGuard batchAccessGuard,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.assessmentRepository = assessmentRepository;
        this.markRepository = markRepository;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
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

        // null testType defaults to OFFLINE, as the DTO comment says
        assessment.setTestType(request.testType() != null ? request.testType() : TestType.OFFLINE);
        assessment.setDurationMinutes(request.durationMinutes());
        assessment.setAvailableFrom(request.availableFrom());
        assessment.setAvailableUntil(request.availableUntil());

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

    /**
     * testType is optional - null means every test type (teacher's test
     * list), ONLINE_MCQ narrows to what the student "take a test" screen
     * wants (see AssessmentRepository's javadoc on the dedicated query
     * method this was written for but, until now, never called).
     */
    public Page<AssessmentResponse> listForBatch(UUID batchId, TestType testType, Pageable pageable) {
        batchAccessGuard.assertCanView(batchId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Page<Assessment> page = testType != null
                ? assessmentRepository.findByTenantIdAndBatchIdAndTestTypeOrderByTestDateDesc(tenantId, batchId,
                        testType, pageable)
                : assessmentRepository.findByTenantIdAndBatchIdOrderByTestDateDesc(tenantId, batchId, pageable);
        return page.map(this::toResponse);
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

    /**
     * Staff-only leaderboard for a batch - every active student's rank,
     * name, and average score, sorted best-first. Same "classmates' results
     * are a staff privilege" rule as listMarksForTest: PARENT/STUDENT get
     * getStudentRank below instead, which never exposes another student's
     * name or score.
     */
    public BatchRankingResponse getBatchRanking(UUID batchId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Batch batch = batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        List<BatchRankingEntry> entries = computeRanking(tenantId, batchId);
        long rankedCount = entries.stream().filter(e -> e.rank() != null).count();
        return new BatchRankingResponse(batchId, batch.getName(), rankedCount, entries);
    }

    /**
     * PARENT/STUDENT-safe view: this one student's own rank and how many
     * classmates they're ranked against, guarded per-student via
     * StudentAccessGuard - same pattern as getStudentMarkHistory.
     */
    public StudentRankResponse getStudentRank(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        if (student.getBatchId() == null) {
            // Not assigned to a batch yet - nothing to rank against.
            return new StudentRankResponse(studentId, null, null, 0, 0.0, 0);
        }

        List<BatchRankingEntry> entries = computeRanking(tenantId, student.getBatchId());
        long rankedCount = entries.stream().filter(e -> e.rank() != null).count();
        BatchRankingEntry mine = entries.stream()
                .filter(e -> e.studentId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Student " + studentId + " missing from their own batch's ranking - should never happen"));

        return new StudentRankResponse(studentId, student.getBatchId(), mine.rank(), rankedCount,
                mine.averagePercentage(), mine.gradedTestCount());
    }

    /**
     * Shared by both ranking endpoints above. A student's rank is their
     * average score percentage across every graded mark in this batch's
     * tests, all-time - not scoped to a period, unlike ReportService's
     * figures, since "what's my rank" is naturally an ongoing standing, not
     * a this-week snapshot. Ties share the same rank (standard competition
     * ranking: 1, 2, 2, 4 - not 1, 2, 2, 3), and a student with zero graded
     * marks gets rank=null rather than being force-ranked last, since "no
     * data yet" and "ranked lowest" are different things a parent shouldn't
     * confuse.
     */
    private List<BatchRankingEntry> computeRanking(UUID tenantId, UUID batchId) {
        List<Student> students = studentRepository.findByTenantIdAndBatchIdAndActiveTrue(tenantId, batchId);
        Map<UUID, List<MarkRepository.StudentScore>> scoresByStudent = markRepository
                .findScoresForBatch(tenantId, batchId).stream()
                .collect(Collectors.groupingBy(MarkRepository.StudentScore::getStudentId));

        record Scored(Student student, double averagePercentage, long gradedCount) {
        }

        List<Scored> scored = students.stream()
                .map(s -> {
                    List<MarkRepository.StudentScore> mine = scoresByStudent.getOrDefault(s.getId(), List.of());
                    double avg = mine.isEmpty() ? 0.0
                            : round1(mine.stream()
                                    .mapToDouble(m -> m.getMarksObtained().doubleValue() / m.getMaxMarks().doubleValue()
                                            * 100)
                                    .average()
                                    .orElse(0.0));
                    return new Scored(s, avg, mine.size());
                })
                .toList();

        List<Scored> ranked = scored.stream()
                .filter(s -> s.gradedCount() > 0)
                .sorted(Comparator.comparingDouble(Scored::averagePercentage).reversed()
                        .thenComparing(s -> s.student().getFullName()))
                .toList();

        List<Scored> unranked = scored.stream()
                .filter(s -> s.gradedCount() == 0)
                .sorted(Comparator.comparing(s -> s.student().getFullName()))
                .toList();

        List<BatchRankingEntry> entries = new ArrayList<>();
        Double previousAverage = null;
        int previousRank = 0;
        int position = 0;
        for (Scored s : ranked) {
            position++;
            int rank = (previousAverage != null && Double.compare(s.averagePercentage(), previousAverage) == 0)
                    ? previousRank
                    : position;
            entries.add(new BatchRankingEntry(s.student().getId(), s.student().getFullName(), rank,
                    s.averagePercentage(), s.gradedCount()));
            previousAverage = s.averagePercentage();
            previousRank = rank;
        }
        for (Scored s : unranked) {
            entries.add(new BatchRankingEntry(s.student().getId(), s.student().getFullName(), null, 0.0, 0));
        }
        return entries;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private AssessmentResponse toResponse(Assessment assessment) {
        return new AssessmentResponse(
                assessment.getId(),
                assessment.getBatchId(),
                assessment.getTitle(),
                assessment.getSubject(),
                assessment.getMaxMarks(),
                assessment.getTestDate(),
                assessment.getTestType(),
                assessment.getDurationMinutes(),
                assessment.getAvailableFrom(),
                assessment.getAvailableUntil(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt());
    }
}
