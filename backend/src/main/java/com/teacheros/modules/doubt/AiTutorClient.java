package com.teacheros.modules.doubt;

import java.util.List;

/**
 * Generates a tutor's reply to a student's question. Swappable the same
 * way SmsSender/EmailSender/OtpSender are (see notification package) -
 * "log" is the dev default (no real API call, no cost, no key required),
 * "anthropic" is the real provider. Both are wired via
 * app.ai.provider + @ConditionalOnProperty, never chosen in code.
 */
public interface AiTutorClient {

    /**
     * @param studentGrade   the student's grade/class if known (e.g. "8",
     *                       "10th"), null if not recorded - lets the
     *                       provider pitch the explanation at the right
     *                       level instead of guessing.
     * @param recentHistory  prior turns in this thread, oldest first,
     *                       already bounded by the caller (DoubtService) -
     *                       this interface does not decide how much
     *                       history is "enough".
     * @param question       the student's new question, already validated
     *                       (non-blank, length-capped) by the caller.
     * @return the tutor's reply as plain text.
     * @throws AiTutorException if generation fails for any reason - the
     *                          caller (DoubtService) decides what the
     *                          student sees when that happens, this method
     *                          never returns a partial or malformed answer.
     */
    String answer(String studentGrade, List<Turn> recentHistory, String question);

    record Turn(DoubtSender sender, String body) {
    }

    class AiTutorException extends RuntimeException {
        public AiTutorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
