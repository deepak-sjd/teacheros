package com.teacheros.modules.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class MarkDtos {

    /**
     * Bulk upsert, matching the same "grade the whole class in one sitting"
     * workflow that MarkAttendanceRequest uses for attendance - a teacher
     * enters marks for every student in one form and submits once, not one
     * API call per student.
     */
    public record SubmitMarksRequest(
            @NotEmpty @Valid List<StudentMarkEntry> entries) {
    }

    public record StudentMarkEntry(
            @NotNull UUID studentId,
            // Null is allowed here (not yet graded) - validated against
            // the test's maxMarks in the service layer, not via a bean
            // validation annotation, since the bound depends on which test
            // this belongs to.
            BigDecimal marksObtained) {
    }

    public record MarkResponse(
            UUID studentId,
            BigDecimal marksObtained) {
    }

    /**
     * One row in a student's own report-card view - the test's own
     * metadata joined with this student's result for it.
     */
    public record StudentMarkHistoryEntry(
            UUID testId,
            String testTitle,
            String subject,
            BigDecimal maxMarks,
            BigDecimal marksObtained,
            LocalDate testDate) {
    }

    /**
     * A student's standing among their own batch-mates - "Rank 5/45" on
     * the report-card mockup. Deliberately anonymous and aggregate-only:
     * unlike listMarksForTest (staff-only, named, per-test), this never
     * reveals who else is in the batch or what any of them individually
     * scored, only this student's own totals and their numeric position.
     * That distinction is why this is safe to expose to PARENT/STUDENT
     * while listMarksForTest is not - see AssessmentService's javadoc on
     * that method.
     *
     * rank is null when this student has no graded marks yet (nothing to
     * rank them by) or has no batch assigned - totalStudents is still
     * populated in that case so the frontend can show batch size either
     * way.
     */
    public record StudentRankResponse(
            BigDecimal totalMarksObtained,
            BigDecimal totalMaxMarks,
            Double percentage,
            Integer rank,
            long totalStudents) {
    }
}
