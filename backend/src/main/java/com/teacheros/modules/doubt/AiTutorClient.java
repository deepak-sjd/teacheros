package com.teacheros.modules.doubt;

import java.math.BigDecimal;
import java.util.List;

/**
 * Generates AI content for the app - a tutor's reply to a student's
 * question (#answer, used by DoubtService), or a teacher's draft quiz
 * questions/study notes (#generateQuestions/#generateNotes, used by
 * com.teacheros.modules.content.ContentGeneratorService). One interface,
 * one set of three swappable providers (log/ollama/anthropic) for all of
 * it - deliberately not a second AI-provider system for content
 * generation, even though the shape of what's generated differs. Swappable
 * the same way SmsSender/EmailSender/OtpSender are (see notification
 * package) - "log" is the dev default (no real API call, no cost, no key
 * required), "anthropic"/"ollama" are real providers. All wired via
 * app.ai.provider + @ConditionalOnProperty, never chosen in code.
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
     * Drafts MCQ questions for a teacher to review/edit and save - see
     * ContentGeneratorService. Unlike #answer this isn't one turn in an
     * ongoing thread (no history, no prior context) and the output is
     * structured rather than free text, since the caller needs to save
     * these straight into an existing test's question list.
     *
     * @param subject    e.g. "Mathematics", null/blank if not specified -
     *                   the provider infers it from topic if so.
     * @param topic      the specific topic to write questions about, e.g.
     *                   "Quadratic equations" - required.
     * @param grade      the target students' grade/class if known, same
     *                   role as studentGrade in #answer.
     * @param difficulty a free-text difficulty hint (e.g. "easy", "medium",
     *                   "hard") - not a closed enum, since providers (and
     *                   future ones) are free to interpret this as
     *                   loosely or precisely as they can.
     * @param count      how many questions to draft, already capped by the
     *                   caller.
     * @return up to {@code count} drafts - may be fewer if the provider's
     *         response is short or partially malformed (bad entries are
     *         dropped, not substituted with blanks); never more than
     *         {@code count}. Callers must not assume exactly {@code count}
     *         comes back.
     * @throws AiTutorException if generation fails entirely (the whole
     *                          call failed, not just one question).
     */
    List<GeneratedQuestion> generateQuestions(
            String subject, String topic, String grade, String difficulty, int count);

    /**
     * Drafts short study notes text on a topic for a teacher to review/
     * edit and save as study material. Freeform plain text, not
     * structured - notes are saved as a plain-text file via the existing
     * study-material upload flow rather than mapped onto any entity's
     * fields, so there's no fixed shape to produce here.
     *
     * @throws AiTutorException if generation fails.
     */
    String generateNotes(String subject, String topic, String grade);

    /**
     * Drafts a personalised study plan focused on a student's weak
     * subjects (used by StudyPlanService). Plain text.
     *
     * This is a default method so existing providers (log/ollama/anthropic)
     * keep compiling without changes - it builds a prompt and reuses
     * #answer. A provider can override it later with its own, more
     * plan-specific prompt or system message.
     *
     * @param studentGrade the student's grade/class if known, null if not.
     * @param weakSubjects the subjects below the weak threshold, as computed
     *                     by the caller - this method does not decide what
     *                     counts as "weak".
     * @throws AiTutorException if generation fails.
     */
    default String studyPlan(String studentGrade, List<SubjectWeakness> weakSubjects) {
        StringBuilder sb = new StringBuilder(
                "Create a short, practical weekly study plan for a student. "
                        + "Their weak subjects (average score, graded tests) are:\n");
        for (SubjectWeakness w : weakSubjects) {
            sb.append("- ").append(w.subject()).append(": ")
                    .append(w.averagePercentage()).append("% across ")
                    .append(w.gradedEntries()).append(" tests\n");
        }
        sb.append("Start with the weakest subject. Plain text, under 200 words.");
        return answer(studentGrade, List.of(), sb.toString());
    }

    record Turn(DoubtSender sender, String body) {
    }

    /**
     * One weak subject for a study plan: its name, the student's average
     * score percentage in it, and how many graded tests that average is
     * based on. Defined here (not in the studyplan package) so this
     * package (doubt) doesn't depend on studyplan's DTOs - same reasoning
     * as GeneratedQuestion below.
     */
    record SubjectWeakness(String subject, double averagePercentage, long gradedEntries) {
    }

    /**
     * One AI-drafted MCQ question, field-for-field the same shape as
     * assessment's QuestionDtos.QuestionInput (see
     * ContentGeneratorService#toDraft) - deliberately NOT that DTO itself,
     * since this package (doubt) has no business depending on assessment's
     * DTO package. The two line up because ContentGeneratorService maps
     * one onto the other directly, field by field.
     */
    record GeneratedQuestion(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctOption,
            BigDecimal marks) {
    }

    class AiTutorException extends RuntimeException {
        public AiTutorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
