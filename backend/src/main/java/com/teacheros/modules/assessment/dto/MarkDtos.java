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
}
