package com.teacheros.modules.studyplan;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.assessment.MarkRepository.SubjectScoreRow;
import com.teacheros.modules.doubt.AiTutorClient;
import com.teacheros.modules.doubt.AiTutorClient.AiTutorException;
import com.teacheros.modules.doubt.AiTutorClient.SubjectWeakness;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.studyplan.dto.StudyPlanDtos.SubjectBreakdown;
import com.teacheros.modules.studyplan.dto.StudyPlanDtos.StudyPlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Weak Topic Detection + Study Plan (Dev6 task). Every method starts with
 * StudentAccessGuard#assertCanView, the same "staff see any student in
 * their tenant, a PARENT/STUDENT only their own child/own record" rule
 * every other per-student endpoint in this codebase follows.
 *
 * Unlike DoubtService#ask, regenerate() is NOT restricted to the STUDENT
 * role. Asking the AI tutor a question is a conversational action that
 * should only happen as the student themselves; generating a study plan
 * from already-graded marks is closer to pulling up a report - a parent
 * or staff member reviewing a student's progress is a completely
 * reasonable reason to request one too.
 */
@Service
public class StudyPlanService {

    private static final Logger log = LoggerFactory.getLogger(StudyPlanService.class);

    private final StudyPlanRepository studyPlanRepository;
    private final MarkRepository markRepository;
    private final StudentRepository studentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;
    private final AiTutorClient aiTutorClient;
    private final double weakSubjectThreshold;

    public StudyPlanService(
            StudyPlanRepository studyPlanRepository,
            MarkRepository markRepository,
            StudentRepository studentRepository,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider,
            AiTutorClient aiTutorClient,
            // A subject below this average percentage (across every graded
            // test in it, all-time) counts as "weak". 60% is a reasonable
            // generic pass/fail-adjacent line for a coaching-centre
            // context; tenants that want a stricter or looser bar can
            // override it without a code change.
            @Value("${app.ai.weak-subject-threshold:60}") double weakSubjectThreshold) {
        this.studyPlanRepository = studyPlanRepository;
        this.markRepository = markRepository;
        this.studentRepository = studentRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
        this.aiTutorClient = aiTutorClient;
        this.weakSubjectThreshold = weakSubjectThreshold;
    }

    /**
     * Read-only - recomputes the subject breakdown fresh, but never calls the AI
     * provider.
     */
    public StudyPlanResponse get(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        List<SubjectBreakdown> subjects = computeBreakdown(tenantId, studentId);
        StudyPlan existing = studyPlanRepository.findByTenantIdAndStudentId(tenantId, studentId).orElse(null);
        return toResponse(subjects, existing);
    }

    /**
     * Recomputes weak subjects, calls the AI provider, and upserts the
     * single plan row for this student. If generation fails, nothing is
     * saved - unlike DoubtService#ask there's no partial conversation
     * state to protect here, so the caller just sees an error and can
     * retry, rather than a placeholder plan being persisted.
     */
    @Transactional
    public StudyPlanResponse regenerate(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        List<SubjectBreakdown> subjects = computeBreakdown(tenantId, studentId);
        List<SubjectWeakness> weak = subjects.stream()
                .filter(SubjectBreakdown::weak)
                .map(s -> new SubjectWeakness(s.subject(), s.averagePercentage(), s.gradedEntries()))
                .toList();

        if (weak.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_WEAK_SUBJECTS",
                    subjects.isEmpty()
                            ? "This student doesn't have any graded marks yet - nothing to analyze."
                            : "No weak subjects right now - nothing for a study plan to focus on.");
        }

        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        String planText;
        try {
            planText = aiTutorClient.studyPlan(student.getGrade(), weak);
        } catch (AiTutorException e) {
            log.error("AI tutor failed to generate a study plan for student {}: {}", studentId, e.getMessage(), e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                    "Could not generate a study plan right now. Please try again in a moment.");
        }

        StudyPlan plan = studyPlanRepository.findByTenantIdAndStudentId(tenantId, studentId)
                .orElseGet(() -> new StudyPlan(tenantId, studentId, planText));
        plan.setPlanText(planText);
        plan.setGeneratedAt(Instant.now());
        studyPlanRepository.save(plan);

        return toResponse(subjects, plan);
    }

    /**
     * Average score percentage per subject across EVERY graded test this
     * student has, all-time - resolving each test's subject the same way
     * AnalyticsService#subjectPerformance does (assessment override, else
     * batch subject, else "General"), so a student's weak-subject list
     * agrees with the staff-facing subject-performance chart rather than
     * using a different resolution rule. Sorted weakest-first.
     */
    private List<SubjectBreakdown> computeBreakdown(UUID tenantId, UUID studentId) {
        List<SubjectScoreRow> rows = markRepository.findSubjectScoresForStudent(tenantId, studentId);

        Map<String, List<Double>> percentagesBySubject = new LinkedHashMap<>();
        for (SubjectScoreRow row : rows) {
            String subject = resolveSubject(row.getAssessmentSubject(), row.getBatchSubject());
            double pct = row.getMarksObtained().doubleValue() / row.getMaxMarks().doubleValue() * 100;
            percentagesBySubject.computeIfAbsent(subject, s -> new ArrayList<>()).add(pct);
        }

        return percentagesBySubject.entrySet().stream()
                .map(e -> {
                    double avg = round1(e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
                    return new SubjectBreakdown(e.getKey(), avg, e.getValue().size(), avg < weakSubjectThreshold);
                })
                .sorted(Comparator.comparingDouble(SubjectBreakdown::averagePercentage))
                .toList();
    }

    private String resolveSubject(String assessmentSubject, String batchSubject) {
        if (assessmentSubject != null && !assessmentSubject.isBlank()) {
            return assessmentSubject;
        }
        if (batchSubject != null && !batchSubject.isBlank()) {
            return batchSubject;
        }
        return "General";
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private StudyPlanResponse toResponse(List<SubjectBreakdown> subjects, StudyPlan plan) {
        return new StudyPlanResponse(subjects, plan != null ? plan.getPlanText() : null,
                plan != null ? plan.getGeneratedAt() : null);
    }
}
