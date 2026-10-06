package com.teacheros.modules.doubt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev default - logs the question instead of calling a real AI provider,
 * and returns a canned reply so the full ask/save/display flow can be
 * developed and tested end-to-end without an API key or per-call cost.
 * Same role as LoggingSmsSender/LoggingEmailSender for their providers.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "log", matchIfMissing = true)
public class LoggingAiTutorClient implements AiTutorClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingAiTutorClient.class);

    @Override
    public String answer(String studentGrade, List<Turn> recentHistory, String question) {
        log.info("[DEV AI TUTOR] grade={} question={} (no real AI call made - dev provider)", studentGrade, question);
        return "This is a placeholder answer from the dev AI provider (app.ai.provider=log). "
                + "Your question was: \"" + question + "\". "
                + "Set app.ai.provider=anthropic and ANTHROPIC_API_KEY to get real answers.";
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(
            String subject, String topic, String grade, String difficulty, int count) {
        log.info("[DEV AI GENERATE] subject={} topic={} grade={} difficulty={} count={} "
                + "(no real AI call made - dev provider)",
                subject, topic, grade, difficulty, count);
        // Well-typed canned drafts, not a canned JSON string to parse -
        // unlike answer()'s free-text reply, these need to be immediately
        // usable by ContentGeneratorService's caller (the teacher reviews/
        // edits them in the Questions editor same as a real provider's
        // output), so "placeholder" here means placeholder CONTENT, not a
        // degraded or skipped response shape.
        int n = Math.max(1, Math.min(count, 20));
        List<GeneratedQuestion> drafts = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            drafts.add(new GeneratedQuestion(
                    "Placeholder question %d on \"%s\" (dev provider - set app.ai.provider=anthropic or ollama "
                            + "for real questions)".formatted(i, topic),
                    "Option A", "Option B", "Option C", "Option D", "A", BigDecimal.ONE));
        }
        return drafts;
    }

    @Override
    public String generateNotes(String subject, String topic, String grade) {
        log.info("[DEV AI GENERATE NOTES] subject={} topic={} grade={} (no real AI call made - dev provider)",
                subject, topic, grade);
        return "This is placeholder study notes from the dev AI provider (app.ai.provider=log) on \"" + topic
                + "\". Set app.ai.provider=anthropic or ollama to get real notes.";
    }
}
