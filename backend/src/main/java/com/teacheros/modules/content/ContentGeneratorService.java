package com.teacheros.modules.content;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.ai.AiFeature;
import com.teacheros.modules.ai.AiUsageService;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateNotesRequest;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateNotesResponse;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateQuestionsRequest;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GenerateQuestionsResponse;
import com.teacheros.modules.content.dto.ContentGeneratorDtos.GeneratedQuestionDraft;
import com.teacheros.modules.doubt.AiTutorClient;
import com.teacheros.modules.doubt.AiTutorClient.AiTutorException;
import com.teacheros.modules.doubt.AiTutorClient.GeneratedQuestion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Teacher-facing AI content drafting: MCQ questions for a test, or study
 * notes text for a batch's study material. Both reuse AiTutorClient - the
 * exact same interface and three swappable providers (log/ollama/
 * anthropic, via app.ai.provider) as the student-facing AI doubt solver
 * (see com.teacheros.modules.doubt) - rather than standing up a second,
 * parallel AI-provider system.
 *
 * Nothing generated here is saved by this service. A question draft is
 * handed back to the teacher to review/edit in the existing Questions
 * editor and saved via the existing PUT /api/v1/tests/{id}/questions
 * endpoint; notes text is handed back to be reviewed/edited and then
 * uploaded via the existing study-material upload endpoint as a plain-
 * text file. This service only drafts - it never writes to the database
 * or to storage (apart from the ai_usage ledger row AiUsageService writes).
 *
 * Every provider call goes through AiUsageService (per-tenant daily limit).
 */
@Service
public class ContentGeneratorService {

    private final AiTutorClient aiTutorClient;
    private final AiUsageService aiUsageService;

    public ContentGeneratorService(AiTutorClient aiTutorClient, AiUsageService aiUsageService) {
        this.aiTutorClient = aiTutorClient;
        this.aiUsageService = aiUsageService;
    }

    public GenerateQuestionsResponse generateQuestions(GenerateQuestionsRequest request) {
        String difficulty = (request.difficulty() == null || request.difficulty().isBlank())
                ? "medium"
                : request.difficulty();
        // @Min/@Max on the request already bounds this 1-20, but a
        // defensive clamp here means this method is still safe to call
        // directly (e.g. from a future internal caller) without relying
        // on bean-validation having run first.
        int count = Math.max(1, Math.min(request.count(), 20));

        List<GeneratedQuestion> drafts;
        try {
            // A tenant over its daily limit gets a 429 ApiException from track(),
            // which is not an AiTutorException and so passes straight through.
            drafts = aiUsageService.track(AiFeature.CONTENT_QUESTIONS,
                    () -> aiTutorClient.generateQuestions(request.subject(), request.topic(), request.grade(),
                            difficulty, count));
        } catch (AiTutorException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED",
                    "Could not generate questions right now. Please try again in a moment.");
        }

        if (drafts.isEmpty()) {
            // Every draft came back malformed/unusable, or the provider
            // returned an empty list - a clearer message than silently
            // handing the teacher an empty Questions editor and letting
            // them wonder what happened.
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED",
                    "The AI didn't return any usable questions. Try again, or try a more specific topic.");
        }

        return new GenerateQuestionsResponse(drafts.stream().map(this::toDraft).toList());
    }

    public GenerateNotesResponse generateNotes(GenerateNotesRequest request) {
        String notes;
        try {
            notes = aiUsageService.track(AiFeature.CONTENT_NOTES,
                    () -> aiTutorClient.generateNotes(request.subject(), request.topic(), request.grade()));
        } catch (AiTutorException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED",
                    "Could not generate notes right now. Please try again in a moment.");
        }

        if (notes == null || notes.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED",
                    "The AI didn't return any notes. Try again, or try a more specific topic.");
        }

        return new GenerateNotesResponse(notes);
    }

    private GeneratedQuestionDraft toDraft(GeneratedQuestion q) {
        return new GeneratedQuestionDraft(
                q.questionText(), q.optionA(), q.optionB(), q.optionC(), q.optionD(), q.correctOption(), q.marks());
    }
}
