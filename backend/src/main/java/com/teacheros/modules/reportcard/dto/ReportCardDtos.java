package com.teacheros.modules.reportcard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ReportCardDtos {

    // ---- Exam terms ----

    public record ExamTermRequest(
            @NotBlank @Size(max = 100) String name,
            // Nullable until Dev 2's academic-year table exists (see V80).
            UUID academicYearId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record ExamTermResponse(
            UUID id,
            UUID academicYearId,
            String name,
            LocalDate startDate,
            LocalDate endDate) {
    }

    // ---- Grade scale ----

    public record GradeBandInput(
            @NotBlank @Size(max = 10) String grade,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minPercentage,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal maxPercentage,
            @Size(max = 100) String remark) {
    }

    /**
     * Full replace, same "edit everything, save once" style as
     * ReplaceQuestionsRequest.
     */
    public record ReplaceGradeScaleRequest(
            @NotEmpty @Valid List<GradeBandInput> bands) {
    }

    public record GradeBandResponse(
            String grade,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            String remark) {
    }

    /**
     * usingDefaults is true when the tenant has not saved its own scale yet. Bands
     * are highest-first.
     */
    public record GradeScaleResponse(
            boolean usingDefaults,
            List<GradeBandResponse> bands) {
    }

    // ---- Term results (computed, never stored) ----

    public record SubjectResult(
            String subject,
            double averagePercentage,
            long gradedEntries,
            String grade,
            String remark) {
    }

    /**
     * One term's result for one student. subjects is empty when nothing in
     * the term is graded yet; overallGrade/overallRemark are then null.
     * teacherRemark is the free-text remark, null if none was entered.
     */
    public record TermResultResponse(
            UUID termId,
            String termName,
            LocalDate startDate,
            LocalDate endDate,
            List<SubjectResult> subjects,
            long gradedEntries,
            double overallPercentage,
            String overallGrade,
            String overallRemark,
            String teacherRemark) {
    }

    // ---- Teacher remarks ----

    public record RemarkEntry(
            @NotNull UUID studentId,
            // Blank/null clears an existing remark.
            @Size(max = 1000) String remark) {
    }

    public record SaveRemarksRequest(
            @NotEmpty @Valid List<RemarkEntry> entries) {
    }

    public record RemarkResponse(
            UUID studentId,
            String remark) {
    }
}
