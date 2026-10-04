package com.teacheros.modules.doubt;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a tutor's reply to a student's question. Swappable the same
 * way SmsSender/EmailSender/OtpSender are (see notification package) -
 * "log" is the dev default (no real API call, no cost, no key required),
 * "anthropic" is the real provider. Both are wired via
 * app.ai.provider + @ConditionalOnProperty, never chosen in code.
 *
 * Also used by StudyPlanService (see studyPlan() below) - the doubt
 * solver was the first feature to need an AI provider, so this interface
 * lives in the `doubt` package, but it's not doubt-specific: reuse it for
 * any future feature that needs one of these three providers rather than
 * standing up a parallel provider-swap system.
 */
public interface AiTutorClient {

    /**
     * @param studentGrade  the student's grade/class if known (e.g. "8",
     *                      "10th"), null if not recorded - lets the
     *                      provider pitch the explanation at the right
     *                      level instead of guessing.
     * @param recentHistory prior turns in this thread, oldest first,
     *                      already bounded by the caller (DoubtService) -
     *                      this interface does not decide how much
     *                      history is "enough".
     * @param question      the student's new question, already validated
     *                      (non-blank, length-capped) by the caller.
     * @return the tutor's reply as plain text.
     * @throws AiTutorException if generation fails for any reason - the
     *                          caller (DoubtService) decides what the
     *                          student sees when that happens, this method
     *                          never returns a partial or malformed answer.
     */
    String answer(String studentGrade, List<Turn> recentHistory, String question);

    /**
     * Generates a short personalized study plan for a student, based on
     * subjects where their graded marks are below a threshold - see
     * StudyPlanService for where "weak" is decided; this method only
     * turns an already-computed weak-subject list into plan text, the
     * same separation of concerns as answer() above (DoubtService decides
     * history/limits before calling in, this interface just generates).
     *
     * @param studentGrade same meaning as in answer() above.
     * @param weakSubjects the student's weak subjects, weakest first,
     *                     already computed by the caller - never empty
     *                     (StudyPlanService doesn't call this with
     *                     nothing to write about).
     * @return the study plan as plain text.
     * @throws AiTutorException same contract as answer() above.
     */
    String studyPlan(String studentGrade, List<SubjectWeakness> weakSubjects);

    record Turn(DoubtSender sender, String body) {
    }

    record SubjectWeakness(String subject, double averagePercentage, long gradedEntries) {
    }

    /**
     * Turns a weak-subject list into the plain-text block every
     * provider's studyPlan() sends as the "here's the data" part of its
     * prompt - shared because it's mechanical data formatting, unlike
     * each provider's own systemPrompt() (persona/tone/guidelines), which
     * stays independently tunable per provider (see
     * OllamaAiTutorClient#studyPlanSystemPrompt's javadoc for why that
     * part specifically isn't shared).
     */
    static String formatWeakSubjects(List<SubjectWeakness> weakSubjects) {
        return weakSubjects.stream()
                .map(w -> "- " + w.subject() + ": " + Math.round(w.averagePercentage()) + "% average across "
                        + w.gradedEntries() + " graded test" + (w.gradedEntries() == 1 ? "" : "s"))
                .collect(Collectors.joining("\n"));
    }

    class AiTutorException extends RuntimeException {
        public AiTutorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
