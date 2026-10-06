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
 * Free, self-hosted AI tutor answers via Ollama (https://ollama.com)
 * running an open-source model (Llama, Phi, Mistral, Gemma, etc.) on your
 * own server - no per-call cost, no API key, no third party billing. This
 * is the "AI Models (Open Source)" option from the product poster.
 *
 * Requires Ollama running somewhere reachable from this app (defaults to
 * localhost, matching a same-host or same-docker-network deployment) with
 * the configured model already pulled, e.g.:
 * ollama pull llama3.2
 * A CPU-only server runs a small model (3B-ish) at usable-but-not-instant
 * speed; a GPU is faster but not required to get this working.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama")
public class OllamaAiTutorClient implements AiTutorClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiTutorClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public OllamaAiTutorClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            // A small model so this also works on a modest CPU-only server -
            // swap for a bigger one (llama3.1, mistral, etc.) if your server
            // has the RAM/GPU for it. Must already be pulled on the Ollama
            // host: `ollama pull <model>`.
            @Value("${app.ai.ollama.model:llama3.2}") String model,
            // Self-hosted CPU inference is much slower than a cloud API -
            // generous default so a real answer isn't cut off as a timeout
            // on modest hardware.
            @Value("${app.ai.ollama.timeout-seconds:120}") int timeoutSeconds) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String answer(String studentGrade, List<Turn> recentHistory, String question) {
        try {
            String requestBody = buildRequestBody(studentGrade, recentHistory, question);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Ollama returned status {}: {}", response.statusCode(), response.body());
                throw new AiTutorException("Ollama returned status " + response.statusCode(), null);
            }

            return extractText(response.body());
        } catch (AiTutorException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            // Most common failure mode for a self-hosted provider: Ollama
            // isn't running, or isn't reachable at app.ai.ollama.base-url
            // (e.g. still pointing at localhost from inside a container).
            throw new AiTutorException(
                    "Could not reach Ollama at " + baseUrl + " - is it running and reachable?", e);
        } catch (Exception e) {
            throw new AiTutorException("Failed to get an answer from Ollama", e);
        }
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(
            String subject, String topic, String grade, String difficulty, int count) {
        String raw = sendSingleTurn(questionsSystemPrompt(),
                questionsUserPrompt(subject, topic, grade, difficulty, count));
        return parseQuestions(raw, count);
    }

    @Override
    public String generateNotes(String subject, String topic, String grade) {
        return sendSingleTurn(notesSystemPrompt(), notesUserPrompt(subject, topic, grade)).strip();
    }

    /**
     * One-shot call with no prior turns - mirrors AnthropicAiTutorClient's
     * #sendSingleTurn. Reuses #extractText (response parsing is identical
     * regardless of what was asked) but not #buildRequestBody, which is
     * shaped around DoubtSender history that doesn't apply here.
     */
    private String sendSingleTurn(String systemPrompt, String userPrompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("stream", false);
            ArrayNode messages = root.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);

            String requestBody;
            try {
                requestBody = objectMapper.writeValueAsString(root);
            } catch (Exception e) {
                throw new AiTutorException("Failed to build Ollama content-generation request", e);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Ollama returned status {}: {}", response.statusCode(), response.body());
                throw new AiTutorException("Ollama returned status " + response.statusCode(), null);
            }

            return extractText(response.body());
        } catch (AiTutorException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new AiTutorException(
                    "Could not reach Ollama at " + baseUrl + " - is it running and reachable?", e);
        } catch (Exception e) {
            throw new AiTutorException("Failed to generate AI content from Ollama", e);
        }
    }

    // Same prompts as AnthropicAiTutorClient - see that class for the
    // reasoning behind each guideline. Duplicated rather than shared for
    // the same reason its systemPrompt() for #answer is duplicated: each
    // provider's prompt needs to stay independently tunable, and
    // open-source models via Ollama generally need more explicit,
    // directive instructions than Claude does to reliably follow a strict
    // output format.
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
                - Output ONLY the JSON array. Do not wrap it in ```json or any other text.
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

    // Same defensive parsing as AnthropicAiTutorClient#parseQuestions -
    // see that method's javadoc. Duplicated for the same "keep each
    // provider independently tunable" reason as the prompts above; also
    // means a parsing bug found via one provider doesn't silently also
    // affect the other without someone noticing both call sites.
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
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt(studentGrade));

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
            throw new AiTutorException("Failed to build Ollama request", e);
        }
    }

    // Same tutoring guardrails as AnthropicAiTutorClient - see that class's
    // javadoc for why this is scoped narrowly to schoolwork. Duplicated
    // rather than shared because open-source models generally need more
    // explicit, directive instructions than Claude does to reliably follow
    // "explain, don't just answer" - keeping each provider's prompt
    // independently tunable matters more here than avoiding a few lines of
    // duplication.
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
                short numbered/bulleted list over long unbroken text. Do not pad the answer with filler.
                - Stick to academic subjects (maths, science, languages, social studies, etc). If asked \
                something unrelated to schoolwork, or something inappropriate for a school-age student, \
                politely decline and steer the conversation back to their studies.
                - If the question is unclear, ask a brief clarifying question instead of guessing.
                """.formatted(gradeContext);
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("message").path("content").asText();
            if (text.isBlank()) {
                throw new AiTutorException("Ollama response contained no message content", null);
            }
            return text;
        } catch (AiTutorException e) {
            throw e;
        } catch (Exception e) {
            throw new AiTutorException("Failed to parse Ollama response", e);
        }
    }
}
