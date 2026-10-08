package com.teacheros.modules.assessment.dto;

import com.teacheros.modules.assessment.dto.QuestionDtos.QuestionForStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AttemptDtos {

    public record AnswerInput(
            @NotNull UUID questionId,
            // Null/blank means the student left it unanswered - not a
            // validation error, just zero marks for that question.
            @Pattern(regexp = "[ABCD]", message = "must be A, B, C or D") String selectedOption) {
    }

    public record SubmitAnswersRequest(
            @NotEmpty @Valid List<AnswerInput> answers) {
    }

    /**
     * Returned by start() - the questions to render, with a soft (client-enforced)
     * deadline.
     */
    public record StartTestResponse(
            UUID attemptId,
            UUID testId,
            String title,
            Integer durationMinutes,
            Instant startedAt,
            Instant softDeadline,
            List<QuestionForStudent> questions) {
    }

    /** Read-only status check - never creates an attempt as a side effect. */
    public record MyAttemptResponse(
            String status, // NOT_STARTED | IN_PROGRESS | SUBMITTED
            Instant startedAt,
            Instant softDeadline,
            BigDecimal score,
            BigDecimal maxMarks) {
    }

    public record AnswerResult(
            UUID questionId,
            String questionText,
            String selectedOption,
            String correctOption,
            boolean correct,
            BigDecimal marksAwarded,
            BigDecimal marksPossible) {
    }

    public record SubmitResultResponse(
            BigDecimal score,
            BigDecimal maxMarks,
            List<AnswerResult> breakdown) {
    }

    /**
     * One row of the teacher-facing results screen (see
     * OnlineTestService#results). Only covers students who have a
     * TestAttempt row (started or submitted) - a student who never opened
     * the test has no row here at all. The frontend already has a pattern
     * for merging a partial per-student list against the full batch
     * roster (see student/Tests.tsx's OnlineTestsSection /
     * my-attempt), so the "not started" case is handled the same way
     * here rather than this endpoint enumerating every roster student
     * itself.
     */
    public record StudentResultRow(
            UUID studentId,
            String status, // IN_PROGRESS | SUBMITTED - never NOT_STARTED, see above
            Instant submittedAt,
            BigDecimal score,
            BigDecimal maxMarks) {
    }

    /**
     * One question's aggregate stats across every SUBMITTED attempt only -
     * an in-progress attempt's answers aren't counted yet, same as how an
     * individual score isn't final until submit() runs.
     */
    public record QuestionStat(
            UUID questionId,
            String questionText,
            String correctOption,
            int correctCount,
            int incorrectCount,
            int unansweredCount,
            // Of students who answered and got it wrong, which single wrong
            // option (A/B/C/D) was picked most - null if nobody got it
            // wrong, or if every wrong answer was unique (no single most
            // common one). A quick signal for "this is the distractor
            // that's actually fooling people," not a full option-by-option
            // histogram - that's one tap away in the Questions editor if a
            // teacher wants the exact text of each option.
            String mostCommonWrongOption) {
    }

    public record TestResultsResponse(
            UUID testId,
            String title,
            BigDecimal maxMarks,
            List<StudentResultRow> students,
            List<QuestionStat> questions) {
    }
}
