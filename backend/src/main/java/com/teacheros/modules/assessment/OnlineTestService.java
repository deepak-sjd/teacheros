package com.teacheros.modules.assessment;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.dto.AttemptDtos.AnswerInput;
import com.teacheros.modules.assessment.dto.AttemptDtos.AnswerResult;
import com.teacheros.modules.assessment.dto.AttemptDtos.MyAttemptResponse;
import com.teacheros.modules.assessment.dto.AttemptDtos.QuestionStat;
import com.teacheros.modules.assessment.dto.AttemptDtos.StartTestResponse;
import com.teacheros.modules.assessment.dto.AttemptDtos.StudentResultRow;
import com.teacheros.modules.assessment.dto.AttemptDtos.SubmitAnswersRequest;
import com.teacheros.modules.assessment.dto.AttemptDtos.SubmitResultResponse;
import com.teacheros.modules.assessment.dto.AttemptDtos.TestResultsResponse;
import com.teacheros.modules.assessment.dto.QuestionDtos.QuestionForStudent;
import com.teacheros.modules.assessment.dto.QuestionDtos.QuestionInput;
import com.teacheros.modules.assessment.dto.QuestionDtos.QuestionResponse;
import com.teacheros.modules.assessment.dto.QuestionDtos.ReplaceQuestionsRequest;
import com.teacheros.modules.batch.BatchAccessGuard;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Everything specific to ONLINE_MCQ tests - question authoring, a student
 * starting an attempt, and auto-graded submission. Deliberately separate
 * from AssessmentService, which stays focused on the type-agnostic
 * lifecycle (create/update/delete/list) both test types share.
 *
 * A submitted attempt's score is upserted into the same `marks` table
 * AssessmentService.submitMarks writes to (same (testId, studentId) key),
 * so report cards, mark history, and ReportService need no changes to
 * pick up online-test results - they already read from `marks`.
 */
@Service
public class OnlineTestService {

    private final AssessmentRepository assessmentRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final MarkRepository markRepository;
    private final BatchAccessGuard batchAccessGuard;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public OnlineTestService(
            AssessmentRepository assessmentRepository,
            TestQuestionRepository testQuestionRepository,
            TestAttemptRepository testAttemptRepository,
            TestAnswerRepository testAnswerRepository,
            MarkRepository markRepository,
            BatchAccessGuard batchAccessGuard,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.assessmentRepository = assessmentRepository;
        this.testQuestionRepository = testQuestionRepository;
        this.testAttemptRepository = testAttemptRepository;
        this.testAnswerRepository = testAnswerRepository;
        this.markRepository = markRepository;
        this.batchAccessGuard = batchAccessGuard;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    // ---- Staff: author questions -----------------------------------

    /**
     * Full replace of a test's question set, then recomputes maxMarks as
     * the sum of question marks - see V11 migration comment for why
     * maxMarks isn't authoritative at creation time for an online test.
     */
    @Transactional
    public List<QuestionResponse> replaceQuestions(UUID testId, ReplaceQuestionsRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = requireOnlineTest(tenantId, testId);

        testQuestionRepository.deleteByTenantIdAndTestId(tenantId, testId);

        List<TestQuestion> saved = testQuestionRepository.saveAll(
                indexed(request.questions()).map(indexedQuestion -> {
                    QuestionInput q = indexedQuestion.value();
                    return new TestQuestion(tenantId, testId, q.questionText(), q.optionA(), q.optionB(),
                            q.optionC(), q.optionD(), q.correctOption(), q.marks(), indexedQuestion.index());
                }).toList());

        BigDecimal totalMarks = saved.stream().map(TestQuestion::getMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
        assessment.setMaxMarks(totalMarks);
        assessmentRepository.save(assessment);

        return saved.stream().sorted(Comparator.comparingInt(TestQuestion::getPosition))
                .map(this::toQuestionResponse).toList();
    }

    /** Staff-only - includes the correct answer, unlike what a student is sent. */
    public List<QuestionResponse> getQuestionsForStaff(UUID testId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        requireOnlineTest(tenantId, testId);
        return testQuestionRepository.findByTenantIdAndTestIdOrderByPositionAsc(tenantId, testId).stream()
                .map(this::toQuestionResponse).toList();
    }

    // ---- Student: take the test -------------------------------------

    /**
     * Idempotent - a second call for the same student returns the same
     * attempt rather than erroring, so re-opening the test page after a
     * refresh doesn't lose progress or reset the clock.
     */
    @Transactional
    public StartTestResponse start(UUID testId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = requireOnlineTest(tenantId, testId);
        batchAccessGuard.assertCanView(assessment.getBatchId());
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();

        Instant now = Instant.now();
        if (assessment.getAvailableFrom() != null && now.isBefore(assessment.getAvailableFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_YET_AVAILABLE", "This test isn't open yet.");
        }
        if (assessment.getAvailableUntil() != null && now.isAfter(assessment.getAvailableUntil())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEST_CLOSED",
                    "This test is no longer accepting new attempts.");
        }

        List<TestQuestion> questions = testQuestionRepository.findByTenantIdAndTestIdOrderByPositionAsc(tenantId,
                testId);
        if (questions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_QUESTIONS", "This test doesn't have any questions yet.");
        }

        TestAttempt attempt = testAttemptRepository.findByTenantIdAndTestIdAndStudentId(tenantId, testId, studentId)
                .orElseGet(() -> testAttemptRepository.save(new TestAttempt(tenantId, testId, studentId, now)));

        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_SUBMITTED", "You've already submitted this test.");
        }

        return new StartTestResponse(
                attempt.getId(), testId, assessment.getTitle(), assessment.getDurationMinutes(),
                attempt.getStartedAt(), softDeadline(assessment, attempt.getStartedAt()),
                questions.stream().map(this::toQuestionForStudent).toList());
    }

    /**
     * Read-only - never creates an attempt, so viewing a test list can't
     * accidentally start the clock.
     */
    public MyAttemptResponse myAttempt(UUID testId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = requireOnlineTest(tenantId, testId);
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();

        return testAttemptRepository.findByTenantIdAndTestIdAndStudentId(tenantId, testId, studentId)
                .map(attempt -> new MyAttemptResponse(
                        attempt.getStatus().name(), attempt.getStartedAt(),
                        softDeadline(assessment, attempt.getStartedAt()), attempt.getScore(), assessment.getMaxMarks()))
                .orElseGet(() -> new MyAttemptResponse("NOT_STARTED", null, null, null, assessment.getMaxMarks()));
    }

    /**
     * Grades against the stored correct_option and upserts the total into
     * `marks` (same table/key AssessmentService.submitMarks writes to).
     * Not time-limited server-side beyond the attempt existing and being
     * IN_PROGRESS - see V11 migration comment on why duration is advisory
     * only, to avoid a slow connection permanently stranding an attempt.
     */
    @Transactional
    public SubmitResultResponse submit(UUID testId, SubmitAnswersRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = requireOnlineTest(tenantId, testId);
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();

        TestAttempt attempt = testAttemptRepository.findByTenantIdAndTestIdAndStudentId(tenantId, testId, studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NOT_STARTED",
                        "Start the test before submitting answers."));
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_SUBMITTED", "You've already submitted this test.");
        }

        List<TestQuestion> questions = testQuestionRepository.findByTenantIdAndTestIdOrderByPositionAsc(tenantId,
                testId);
        Map<UUID, TestQuestion> byId = questions.stream().collect(Collectors.toMap(TestQuestion::getId, q -> q));
        Map<UUID, String> submitted = request.answers().stream()
                .filter(a -> byId.containsKey(a.questionId()))
                .collect(Collectors.toMap(AnswerInput::questionId, a -> emptyToNull(a.selectedOption()), (a, b) -> a));

        BigDecimal totalScore = BigDecimal.ZERO;
        List<AnswerResult> breakdown = new java.util.ArrayList<>();
        List<TestAnswer> toSave = new java.util.ArrayList<>();

        for (TestQuestion q : questions) {
            String selected = submitted.get(q.getId());
            boolean correct = selected != null && selected.equals(q.getCorrectOption());
            BigDecimal awarded = correct ? q.getMarks() : BigDecimal.ZERO;
            totalScore = totalScore.add(awarded);

            toSave.add(new TestAnswer(tenantId, attempt.getId(), q.getId(), selected, correct, awarded));
            breakdown.add(new AnswerResult(q.getId(), q.getQuestionText(), selected, q.getCorrectOption(),
                    correct, awarded, q.getMarks()));
        }
        testAnswerRepository.saveAll(toSave);

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        attempt.setScore(totalScore);
        testAttemptRepository.save(attempt);

        upsertMark(tenantId, testId, studentId, totalScore);

        return new SubmitResultResponse(totalScore, assessment.getMaxMarks(), breakdown);
    }

    /**
     * Teacher-facing results: every attempt's status/score, plus
     * per-question stats computed only from SUBMITTED attempts (an
     * in-progress attempt has TestAnswer rows too - partial answers
     * saved so far - but counting those would make "unansweredCount"
     * misleading while someone is mid-attempt, so they're excluded here
     * the same way submit() is what finalizes a score).
     */
    public TestResultsResponse results(UUID testId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Assessment assessment = requireOnlineTest(tenantId, testId);
        batchAccessGuard.assertCanView(assessment.getBatchId());

        List<TestAttempt> attempts = testAttemptRepository.findByTenantIdAndTestId(tenantId, testId);
        List<StudentResultRow> studentRows = attempts.stream()
                .map(a -> new StudentResultRow(a.getStudentId(), a.getStatus().name(), a.getSubmittedAt(),
                        a.getScore(), assessment.getMaxMarks()))
                .toList();

        List<UUID> submittedAttemptIds = attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED)
                .map(TestAttempt::getId)
                .toList();

        List<TestQuestion> questions = testQuestionRepository.findByTenantIdAndTestIdOrderByPositionAsc(tenantId,
                testId);
        List<TestAnswer> answers = submittedAttemptIds.isEmpty()
                ? List.of()
                : testAnswerRepository.findByTenantIdAndAttemptIdIn(tenantId, submittedAttemptIds);
        Map<UUID, List<TestAnswer>> answersByQuestion = answers.stream()
                .collect(Collectors.groupingBy(TestAnswer::getQuestionId));

        List<QuestionStat> questionStats = questions.stream()
                .map(q -> toQuestionStat(q, answersByQuestion.getOrDefault(q.getId(), List.of())))
                .toList();

        return new TestResultsResponse(testId, assessment.getTitle(), assessment.getMaxMarks(), studentRows,
                questionStats);
    }

    private QuestionStat toQuestionStat(TestQuestion question, List<TestAnswer> answersForQuestion) {
        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;
        Map<String, Integer> wrongOptionCounts = new java.util.HashMap<>();

        for (TestAnswer a : answersForQuestion) {
            if (a.getSelectedOption() == null) {
                unanswered++;
            } else if (Boolean.TRUE.equals(a.getIsCorrect())) {
                correct++;
            } else {
                incorrect++;
                wrongOptionCounts.merge(a.getSelectedOption(), 1, Integer::sum);
            }
        }

        // Only reported when there's a single clear leader - a tie (or no
        // wrong answers at all) isn't a meaningfully "most common" one, so
        // null is more honest than picking an arbitrary winner.
        String mostCommonWrong = wrongOptionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(leader -> wrongOptionCounts.values().stream().filter(v -> v.equals(leader.getValue()))
                        .count() == 1)
                .map(Map.Entry::getKey)
                .orElse(null);

        return new QuestionStat(question.getId(), question.getQuestionText(), question.getCorrectOption(),
                correct, incorrect, unanswered, mostCommonWrong);
    }

    // ---- helpers -------------------------------------------------

    private Assessment requireOnlineTest(UUID tenantId, UUID testId) {
        Assessment assessment = assessmentRepository.findByIdAndTenantId(testId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Test", testId));
        if (assessment.getTestType() != TestType.ONLINE_MCQ) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ONLINE_TEST", "This test isn't an online test.");
        }
        return assessment;
    }

    private Instant softDeadline(Assessment assessment, Instant startedAt) {
        return assessment.getDurationMinutes() != null
                ? startedAt.plus(assessment.getDurationMinutes(), ChronoUnit.MINUTES)
                : null;
    }

    private void upsertMark(UUID tenantId, UUID testId, UUID studentId, BigDecimal score) {
        Mark mark = markRepository.findByTenantIdAndTestIdAndStudentId(tenantId, testId, studentId)
                .orElseGet(() -> new Mark(tenantId, testId, studentId, score));
        mark.setMarksObtained(score);
        markRepository.save(mark);
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private QuestionResponse toQuestionResponse(TestQuestion q) {
        return new QuestionResponse(q.getId(), q.getQuestionText(), q.getOptionA(), q.getOptionB(), q.getOptionC(),
                q.getOptionD(), q.getCorrectOption(), q.getMarks(), q.getPosition());
    }

    private QuestionForStudent toQuestionForStudent(TestQuestion q) {
        return new QuestionForStudent(q.getId(), q.getQuestionText(), q.getOptionA(), q.getOptionB(), q.getOptionC(),
                q.getOptionD(), q.getMarks(), q.getPosition());
    }

    private record Indexed<T>(int index, T value) {
    }

    private <T> java.util.stream.Stream<Indexed<T>> indexed(List<T> list) {
        return java.util.stream.IntStream.range(0, list.size()).mapToObj(i -> new Indexed<>(i, list.get(i)));
    }
}
