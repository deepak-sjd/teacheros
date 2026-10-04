package com.teacheros.modules.doubt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

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
    public String studyPlan(String studentGrade, List<SubjectWeakness> weakSubjects) {
        String subjects = weakSubjects.stream().map(SubjectWeakness::subject).collect(Collectors.joining(", "));
        log.info("[DEV AI TUTOR] studyPlan grade={} weakSubjects=[{}] (no real AI call made - dev provider)",
                studentGrade, subjects);
        return "This is a placeholder study plan from the dev AI provider (app.ai.provider=log).\n\n"
                + "Weak subjects detected:\n" + AiTutorClient.formatWeakSubjects(weakSubjects) + "\n\n"
                + "Set app.ai.provider=anthropic and ANTHROPIC_API_KEY to get a real personalized plan.";
    }
}
