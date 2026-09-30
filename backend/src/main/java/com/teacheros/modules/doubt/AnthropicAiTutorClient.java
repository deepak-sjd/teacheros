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

    public AnthropicAiTutorClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.anthropic.api-key}") String apiKey,
            // Override via ANTHROPIC_MODEL if/when a newer model should be
            // used - deliberately not hardcoded deeper than this one
            // default, since model names change over time.
            @Value("${app.ai.anthropic.model:claude-sonnet-5}") String model,
            @Value("${app.ai.anthropic.max-tokens:500}") int maxTokens) {
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
