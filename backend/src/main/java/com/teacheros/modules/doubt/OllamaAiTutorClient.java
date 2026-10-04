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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

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
    public String studyPlan(String studentGrade, List<SubjectWeakness> weakSubjects) {
        try {
            String requestBody = buildStudyPlanRequestBody(studentGrade, weakSubjects);

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
            throw new AiTutorException("Failed to get a study plan from Ollama", e);
        }
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

    private String buildStudyPlanRequestBody(String studentGrade, List<SubjectWeakness> weakSubjects) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", studyPlanSystemPrompt(studentGrade));

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", AiTutorClient.formatWeakSubjects(weakSubjects));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiTutorException("Failed to build Ollama request", e);
        }
    }

    // Independently tunable from the doubt-answering systemPrompt() below -
    // see AiTutorClient#formatWeakSubjects's javadoc for why the data
    // formatting IS shared while this persona/tone text is not.
    private String studyPlanSystemPrompt(String studentGrade) {
        String gradeContext = (studentGrade == null || studentGrade.isBlank())
                ? "a school or coaching-centre student"
                : "a grade/class " + studentGrade + " student";
        return """
                You are a friendly, encouraging study coach for %s in India, used inside a school/coaching \
                management app. You've been given a list of subjects where this student is currently \
                scoring lower than their other subjects, based on their graded test marks.

                Guidelines:
                - Write a short, encouraging, actionable study plan (roughly 150-250 words) that helps the \
                student improve specifically in these subjects.
                - Suggest concrete next steps - what to revise, how to practice, how to split time across \
                the subjects - rather than generic advice like "study harder".
                - Keep the tone positive and motivating, not discouraging - a low score is a starting \
                point to improve from, not a verdict.
                - Format as short paragraphs or a short bulleted list, readable on a phone screen.
                - Base the plan only on the subjects and scores given - do not invent subjects or scores \
                that weren't provided.
                """.formatted(gradeContext);
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
