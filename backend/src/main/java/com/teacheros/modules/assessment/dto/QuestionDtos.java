package com.teacheros.modules.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class QuestionDtos {

    public record QuestionInput(
            @NotBlank String questionText,
            @NotBlank String optionA,
            @NotBlank String optionB,
            @NotBlank String optionC,
            @NotBlank String optionD,
            @NotNull @Pattern(regexp = "[ABCD]", message = "must be A, B, C or D") String correctOption,
            @NotNull @DecimalMin(value = "0.01") BigDecimal marks) {
    }

    /**
     * Full replace, not incremental edits - same "grade the whole class in
     * one sitting" style as SubmitMarksRequest: a teacher edits the whole
     * question set in one screen and saves once. Position is derived from
     * list order, not sent by the client.
     */
    public record ReplaceQuestionsRequest(
            @NotEmpty @Valid List<QuestionInput> questions) {
    }

    /** Staff view while authoring/reviewing - includes the correct answer. */
    public record QuestionResponse(
            UUID id,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctOption,
            BigDecimal marks,
            int position) {
    }

    /** What a student sees while taking the test - correct answer withheld. */
    public record QuestionForStudent(
            UUID id,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            BigDecimal marks,
            int position) {
    }
}
