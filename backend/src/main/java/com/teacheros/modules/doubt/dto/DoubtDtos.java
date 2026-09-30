package com.teacheros.modules.doubt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DoubtDtos {

    public record AskDoubtRequest(
            @NotBlank @Size(max = 1000) String question) {
    }

    public record DoubtMessageResponse(
            UUID id,
            String sender, // STUDENT or AI
            String body,
            Instant createdAt) {
    }

    /**
     * Response to POST /ask - both the student's own question (echoed back
     * with its server-assigned id/timestamp) and the AI's reply, so the
     * frontend can append both to the thread from a single round trip
     * instead of re-fetching the whole list.
     */
    public record AskDoubtResponse(
            DoubtMessageResponse question,
            DoubtMessageResponse answer) {
    }

    public record DoubtThreadResponse(
            List<DoubtMessageResponse> messages,
            int questionsAskedToday,
            int dailyQuestionLimit) {
    }
}
