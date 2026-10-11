package com.teacheros.modules.reportcard;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.assessment.MarkRepository.SubjectScoreRow;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.GradeBandResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.RemarkEntry;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.RemarkResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.SaveRemarksRequest;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.SubjectResult;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.TermResultResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Term results are computed on demand from `marks` - same "never store a
 * derived number" rule as ReportService/StudyPlanService. A graded test
 * counts toward a term when its test_date falls inside the term's date range.
 * Subject resolution (assessment override, else batch subject, else
 * "General") and the average-of-percentages maths match StudyPlanService and
 * AssessmentService#computeRanking, so figures agree across screens.
 */
@Service
public class ReportCardService {

    private final ExamTermRepository examTermRepository;
    private final ReportCardRemarkRepository remarkRepository;
    private final MarkRepository markRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final GradeScaleService gradeScaleService;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public ReportCardService(
            ExamTermRepository examTermRepository,
            ReportCardRemarkRepository remarkRepository,
            MarkRepository markRepository,
            StudentRepository studentRepository,
            BatchRepository batchRepository,
            GradeScaleService gradeScaleService,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.examTermRepository = examTermRepository;
        this.remarkRepository = remarkRepository;
        this.markRepository = markRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.gradeScaleService = gradeScaleService;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    /**
     * Every configured term's result for one student, oldest term first.
     * Empty list when the tenant has no terms. Guarded per-student like every
     * other student-scoped read, so a PARENT/STUDENT only gets their own.
     */
    public List<TermResultResponse> getStudentResults(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        List<ExamTerm> terms = examTermRepository.findByTenantIdOrderByStartDateAsc(tenantId);
        if (terms.isEmpty()) {
            return List.of();
        }

        List<GradeBandResponse> bands = gradeScaleService.effectiveBands(tenantId);
        Map<UUID, String> remarksByTerm = remarkRepository.findByTenantIdAndStudentId(tenantId, studentId)
                .stream().collect(Collectors.toMap(ReportCardRemark::getExamTermId, ReportCardRemark::getRemark));

        return terms.stream()
                .map(t -> buildTermResult(tenantId, studentId, t, bands, remarksByTerm.get(t.getId())))
                .toList();
    }

    /**
     * Staff: existing remarks for one term, limited to the active students of one
     * batch.
     */
    public List<RemarkResponse> listRemarks(UUID termId, UUID batchId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        requireTerm(tenantId, termId);
        batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        Set<UUID> batchStudentIds = studentRepository.findByTenantIdAndBatchIdAndActiveTrue(tenantId, batchId)
                .stream().map(Student::getId).collect(Collectors.toSet());

        return remarkRepository.findByTenantIdAndExamTermId(tenantId, termId).stream()
                .filter(r -> batchStudentIds.contains(r.getStudentId()))
                .map(r -> new RemarkResponse(r.getStudentId(), r.getRemark()))
                .toList();
    }

    /**
     * Staff bulk upsert, same "whole class, one submission" shape as marks
     * and attendance. A blank remark deletes that student's remark. All
     * students are checked against the tenant first, and the whole call is
     * one transaction, so a bad ID doesn't half-save the class.
     */
    @Transactional
    public void saveRemarks(UUID termId, SaveRemarksRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();
        requireTerm(tenantId, termId);

        for (RemarkEntry entry : request.entries()) {
            studentRepository.findByIdAndTenantId(entry.studentId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student", entry.studentId()));
        }

        for (RemarkEntry entry : request.entries()) {
            String text = entry.remark() == null ? "" : entry.remark().trim();
            var existing = remarkRepository.findByTenantIdAndExamTermIdAndStudentId(tenantId, termId,
                    entry.studentId());
            if (text.isEmpty()) {
                existing.ifPresent(remarkRepository::delete);
            } else if (existing.isPresent()) {
                existing.get().setRemark(text);
                remarkRepository.save(existing.get());
            } else {
                remarkRepository.save(new ReportCardRemark(tenantId, entry.studentId(), termId, text, userId));
            }
        }
    }

    private TermResultResponse buildTermResult(UUID tenantId, UUID studentId, ExamTerm term,
            List<GradeBandResponse> bands, String teacherRemark) {
        List<SubjectScoreRow> rows = markRepository.findSubjectScoresForStudentInRange(tenantId, studentId,
                term.getStartDate(), term.getEndDate());

        Map<String, List<Double>> bySubject = new TreeMap<>();
        List<Double> all = new ArrayList<>();
        for (SubjectScoreRow row : rows) {
            double pct = row.getMarksObtained().doubleValue() / row.getMaxMarks().doubleValue() * 100;
            bySubject.computeIfAbsent(resolveSubject(row.getAssessmentSubject(), row.getBatchSubject()),
                    s -> new ArrayList<>()).add(pct);
            all.add(pct);
        }

        List<SubjectResult> subjects = bySubject.entrySet().stream().map(e -> {
            double avg = average(e.getValue());
            GradeBandResponse band = GradeScaleService.resolve(bands, avg);
            return new SubjectResult(e.getKey(), avg, e.getValue().size(),
                    band != null ? band.grade() : null, band != null ? band.remark() : null);
        }).toList();

        double overall = all.isEmpty() ? 0.0 : average(all);
        GradeBandResponse overallBand = all.isEmpty() ? null : GradeScaleService.resolve(bands, overall);

        return new TermResultResponse(term.getId(), term.getName(), term.getStartDate(), term.getEndDate(),
                subjects, all.size(), overall,
                overallBand != null ? overallBand.grade() : null,
                overallBand != null ? overallBand.remark() : null,
                teacherRemark);
    }

    private ExamTerm requireTerm(UUID tenantId, UUID termId) {
        return examTermRepository.findByIdAndTenantId(termId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam term", termId));
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

    private double average(List<Double> values) {
        return Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 10) / 10.0;
    }
}
