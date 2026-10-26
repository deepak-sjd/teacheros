package com.teacheros.modules.academic.dto;

import com.teacheros.modules.academic.PromotionAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PromotionDtos {

    /**
     * Send every promoted student of fromSectionId to toSectionId (overrides the
     * same-name rule).
     */
    public record SectionMapping(
            @NotNull UUID fromSectionId,
            @NotNull UUID toSectionId) {
    }

    /** Overrides the default action for one student, e.g. DETAIN or LEAVE. */
    public record StudentActionOverride(
            @NotNull UUID studentId,
            @NotNull PromotionAction action) {
    }

    /**
     * The same body is used for preview and apply, so what you previewed is what
     * gets applied.
     */
    public record PromotionRequest(
            @NotNull UUID fromAcademicYearId,
            // null = the next year by name (created on apply if it doesn't exist)
            UUID toAcademicYearId,
            // null/empty = every section
            List<UUID> sectionIds,
            @Valid List<SectionMapping> sectionMappings,
            @Valid List<StudentActionOverride> studentActions,
            // Only affects a year created by this call. null = true.
            Boolean setNewYearCurrent) {
    }

    /**
     * issue is non-null when the student has no valid target; apply refuses until
     * it is fixed.
     */
    public record PromotionRow(
            UUID studentId,
            String fullName,
            String rollNumber,
            UUID fromSectionId,
            String fromSectionName,
            String fromClassName,
            UUID toSectionId,
            String toSectionName,
            String toClassName,
            PromotionAction action,
            String note,
            String issue) {
    }

    public record PromotionPreviewResponse(
            UUID fromAcademicYearId,
            String fromAcademicYearName,
            // null when the target year does not exist yet (toYearWillBeCreated = true)
            UUID toAcademicYearId,
            String toAcademicYearName,
            LocalDate toStartDate,
            LocalDate toEndDate,
            boolean toYearWillBeCreated,
            int promoteCount,
            int detainCount,
            int leaveCount,
            int issueCount,
            List<PromotionRow> rows,
            List<String> warnings) {
    }

    public record PromotionResultResponse(
            UUID toAcademicYearId,
            String toAcademicYearName,
            boolean createdYear,
            int promoted,
            int detained,
            int left) {
    }
}
