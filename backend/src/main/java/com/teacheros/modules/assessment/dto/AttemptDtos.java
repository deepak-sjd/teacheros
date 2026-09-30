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
}
