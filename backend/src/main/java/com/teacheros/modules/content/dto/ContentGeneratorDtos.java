package com.teacheros.modules.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public class ContentGeneratorDtos {

    public record GenerateQuestionsRequest(
            @NotBlank String topic,
            String subject,
            String grade,
            // Free text ("easy"/"medium"/"hard" or anything else a
            // teacher types) - see AiTutorClient#generateQuestions on why
            // this isn't a closed enum. Blank/null defaults to "medium" in
            // ContentGeneratorService.
            String difficulty,
            @Min(1) @Max(20) int count) {
    }

    /**
     * Field-for-field the same shape as assessment's
     * QuestionDtos.QuestionInput - deliberately so, so the frontend can
     * drop these straight into the same editable question list it already
     * builds from that type, with no reshaping. See
     * ContentGeneratorService#toDraft.
     */
    public record GeneratedQuestionDraft(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctOption,
            BigDecimal marks) {
    }

    public record GenerateQuestionsResponse(List<GeneratedQuestionDraft> questions) {
    }

    public record GenerateNotesRequest(
            @NotBlank String topic,
            String subject,
            String grade) {
    }

    public record GenerateNotesResponse(String notes) {
    }
}
