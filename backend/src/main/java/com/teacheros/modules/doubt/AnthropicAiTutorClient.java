package com.teacheros.modules.doubt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Real AI tutor answers via the Anthropic Messages API
 * (https://api.anthropic.com/v1/messages). Plain java.net.http.HttpClient
 * rather than a new SDK dependency - this is the only call site, and the
 * request/response shape needed here is small enough that pulling in a
 * whole client library isn't worth it. If a second Anthropic-API call site
 * appears elsewhere in this codebase, factor this out into a shared
 * common/ai client instead of duplicating it.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic")
public class AnthropicAiTutorClient implements AiTutorClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiTutorClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final int contentMaxTokens;

    public AnthropicAiTutorClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.anthropic.api-key}") String apiKey,
            // Override via ANTHROPIC_MODEL if/when a newer model should be
            // used - deliberately not hardcoded deeper than this one
            // default, since model names change over time.
            @Value("${app.ai.anthropic.model:claude-sonnet-5}") String model,
            @Value("${app.ai.anthropic.max-tokens:500}") int maxTokens,
            // A tutoring answer is a short paragraph; a batch of MCQ
            // questions (or a page of study notes) is a lot more text, so
            // content generation gets its own, larger budget rather than
            // reusing maxTokens and silently truncating mid-question.
            @Value("${app.ai.anthropic.content-max-tokens:2000}") int contentMaxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            // Fail fast at startup, same rule as JWT_SECRET/DB_* in prod -
            // better than every first doubt-solver request failing at
            // runtime with a confusing 401 from Anthropic.
            throw new IllegalStateException(
                    "app.ai.provider=anthropic but ANTHROPIC_API_KEY is not set. "
                            + "Set it, or switch app.ai.provider back to 'log' for local dev.");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.contentMaxTokens = contentMaxTokens;
    }

    @Override
    public String answer(String studentGrade, List<Turn> recentHistory, String question) {
        try {
            String requestBody = buildRequestBody(studentGrade, recentHistory, question);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Anthropic API returned status {}: {}", response.statusCode(), response.body());
                throw new AiTutorException("Anthropic API returned status " + response.statusCode(), null);
            }

            return extractText(response.body());
        } catch (AiTutorException e) {
            throw e;
        } catch (Exception e) {
            throw new AiTutorException("Failed to get an answer from the AI tutor", e);
        }
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(
            String subject, String topic, String grade, String difficulty, int count) {
        String raw = sendSingleTurn(
                questionsSystemPrompt(), questionsUserPrompt(subject, topic, grade, difficulty, count),
                contentMaxTokens);
        return parseQuestions(raw, count);
    }

    @Override
    public String generateNotes(String subject, String topic, String grade) {
        return sendSingleTurn(notesSystemPrompt(), notesUserPrompt(subject, topic, grade), contentMaxTokens).strip();
    }

    /**
     * One-shot call with no prior turns - both content-generation methods
     * are a single instruction-in, text-out exchange, unlike #answer's
     * ongoing thread. Reuses #extractText (response parsing is identical
     * regardless of what was asked) but intentionally does NOT reuse
     * #buildRequestBody, which is shaped around DoubtSender history that
     * doesn't apply here.
     */
    private String sendSingleTurn(String systemPrompt, String userPrompt, int tokenBudget) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", tokenBudget);
            root.put("system", systemPrompt);
            ArrayNode messages = root.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", userPrompt);

            String requestBody;
            try {
                requestBody = objectMapper.writeValueAsString(root);
            } catch (Exception e) {
                throw new AiTutorException("Failed to build AI content-generation request", e);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Anthropic API returned status {}: {}", response.statusCode(), response.body());
                throw new AiTutorException("Anthropic API returned status " + response.statusCode(), null);
            }

            return extractText(response.body());
        } catch (AiTutorException e) {
            throw e;
        } catch (Exception e) {
            throw new AiTutorException("Failed to generate AI content", e);
        }
    }

    private String questionsSystemPrompt() {
        return """
                You draft multiple-choice quiz questions for a teacher at a school/coaching centre in India to \
                review, edit, and use in a test. The teacher has full control to edit or discard anything you \
                write before it's ever shown to a student - draft confidently.

                Respond with ONLY a JSON array, no markdown code fences, no commentary before or after it. Each \
                element must have exactly these fields:
                  "questionText": string
                  "optionA": string
                  "optionB": string
                  "optionC": string
                  "optionD": string
                  "correctOption": one of "A", "B", "C", "D"
                  "marks": a positive number (e.g. 1)

                Guidelines:
                - Four distinct, plausible options per question - wrong options should be realistic mistakes a \
                student could make, not obviously silly.
                - Vary which letter is correct across questions - do not make every correct answer "A".
                - Questions should be answerable from the stated topic alone, without external context.
                - Keep wording concise and exam-appropriate for the stated grade/difficulty.
                """;
    }

    private String questionsUserPrompt(String subject, String topic, String grade, String difficulty, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("Write ").append(count).append(" multiple-choice questions");
        if (subject != null && !subject.isBlank())
            sb.append(" for the subject \"").append(subject).append("\"");
        sb.append(" on the topic \"").append(topic).append("\"");
        if (grade != null && !grade.isBlank())
            sb.append(", for grade/class ").append(grade).append(" students");
        sb.append(", at ").append(difficulty == null || difficulty.isBlank() ? "medium" : difficulty)
                .append(" difficulty.");
        return sb.toString();
    }

    private String notesSystemPrompt() {
        return """
                You draft short study notes for a teacher at a school/coaching centre in India to review, edit, \
                and share with students as study material. The teacher has full control to edit anything you \
                write before students see it - draft confidently.

                Respond with the notes themselves only - no preamble like "Here are your notes", no markdown \
                code fences around the whole thing. Plain text with simple structure (short headings, bullet \
                points where useful) is fine; keep it focused and exam-relevant rather than exhaustive.
                """;
    }

    private String notesUserPrompt(String subject, String topic, String grade) {
        StringBuilder sb = new StringBuilder();
        sb.append("Write study notes");
        if (subject != null && !subject.isBlank())
            sb.append(" for the subject \"").append(subject).append("\"");
        sb.append(" on the topic \"").append(topic).append("\"");
        if (grade != null && !grade.isBlank())
            sb.append(", for grade/class ").append(grade).append(" students");
        sb.append(".");
        return sb.toString();
    }

    /**
     * Defensive, not strict: a malformed or incomplete entry is dropped
     * rather than failing the whole batch - a teacher getting 7 good
     * questions back from a request for 8 is a minor inconvenience, not an
     * error, and strips any markdown fences the model adds despite being
     * told not to (small/self-hosted models in particular tend to ignore
     * that instruction more often than Claude does).
     */
    private List<GeneratedQuestion> parseQuestions(String raw, int count) {
        String json = stripJsonFences(raw);
        List<GeneratedQuestion> drafts = new ArrayList<>();
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) {
                throw new AiTutorException("AI response was not a JSON array of questions", null);
            }
            for (JsonNode q : arr) {
                if (drafts.size() >= count)
                    break;
                String questionText = q.path("questionText").asText(null);
                String optionA = q.path("optionA").asText(null);
                String optionB = q.path("optionB").asText(null);
                String optionC = q.path("optionC").asText(null);
                String optionD = q.path("optionD").asText(null);
                String correctOption = q.path("correctOption").asText(null);
                if (isBlank(questionText) || isBlank(optionA) || isBlank(optionB)
                        || isBlank(optionC) || isBlank(optionD)
                        || correctOption == null || !correctOption.matches("[ABCD]")) {
                    log.warn("Dropping malformed AI-generated question: {}", q);
                    continue;
                }
                BigDecimal marks = q.path("marks").isNumber() ? q.path("marks").decimalValue() : BigDecimal.ONE;
                if (marks.signum() <= 0)
                    marks = BigDecimal.ONE;
                drafts.add(new GeneratedQuestion(
                        questionText, optionA, optionB, optionC, optionD, correctOption, marks));
            }
        } catch (AiTutorException e) {
            throw e;
        } catch (Exception e) {
            throw new AiTutorException("Failed to parse AI-generated questions", e);
        }
        return drafts;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String stripJsonFences(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1)
                trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```"))
                trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }

    private String buildRequestBody(String studentGrade, List<Turn> recentHistory, String question) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("system", systemPrompt(studentGrade));

        ArrayNode messages = root.putArray("messages");
        for (Turn turn : recentHistory) {
            ObjectNode message = messages.addObject();
            message.put("role", turn.sender() == DoubtSender.STUDENT ? "user" : "assistant");
            message.put("content", turn.body());
        }
        ObjectNode newMessage = messages.addObject();
        newMessage.put("role", "user");
        newMessage.put("content", question);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiTutorException("Failed to build AI tutor request", e);
        }
    }

    /**
     * Kept intentionally narrow: a homework/exam-prep helper for a school
     * or coaching-centre student, not a general-purpose assistant. Steers
     * toward explaining concepts rather than just handing over final
     * answers to graded work, keeps replies short enough to read
     * comfortably on a phone, and declines anything outside schoolwork or
     * inappropriate for a school-age audience.
     */
    private String systemPrompt(String studentGrade) {
        String gradeContext = (studentGrade == null || studentGrade.isBlank())
                ? "a school or coaching-centre student"
                : "a grade/class " + studentGrade + " student";
        return """
                You are a friendly, patient study helper for %s in India, used inside a school/coaching \
                management app. A student is asking you a doubt about their schoolwork.

                Guidelines:
                - Explain the concept step by step, in simple language suited to their level, rather than \
                just stating a final answer - the goal is understanding, not shortcuts on graded homework \
                or tests.
                - Keep the answer focused and readable on a phone screen: prefer short paragraphs or a \
                short numbered/bulleted list over long unbroken text.
                - Stick to academic subjects (maths, science, languages, social studies, etc). If asked \
                something unrelated to schoolwork, or something inappropriate for a school-age student, \
                politely decline and steer the conversation back to their studies.
                - If the question is unclear, ask a brief clarifying question instead of guessing.
                """.formatted(gradeContext);
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            StringBuilder text = new StringBuilder();
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        text.append(block.path("text").asText());
                    }
                }
            }
            if (text.isEmpty()) {
                throw new AiTutorException("AI tutor response contained no text content", null);
            }
            return text.toString();
        } catch (AiTutorException e) {
            throw e;
        } catch (Exception e) {
            throw new AiTutorException("Failed to parse AI tutor response", e);
        }
    }
}
