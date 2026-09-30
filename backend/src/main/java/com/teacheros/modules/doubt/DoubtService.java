package com.teacheros.modules.doubt;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.doubt.AiTutorClient.AiTutorException;
import com.teacheros.modules.doubt.AiTutorClient.Turn;
import com.teacheros.modules.doubt.dto.DoubtDtos.AskDoubtRequest;
import com.teacheros.modules.doubt.dto.DoubtDtos.AskDoubtResponse;
import com.teacheros.modules.doubt.dto.DoubtDtos.DoubtMessageResponse;
import com.teacheros.modules.doubt.dto.DoubtDtos.DoubtThreadResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * One thread per student (see V11 migration). Every method starts with
 * StudentAccessGuard#assertCanView, same as Message/Fee/Assessment
 * services - staff can view any thread in their tenant, a PARENT/STUDENT
 * only their own child's/own thread.
 *
 * Unlike messaging, only the STUDENT role may actually ask a question
 * here (see #ask below) - the poster/product spec lists "AI doubt
 * solving" under student-facing features, not parent-facing ones. A
 * parent can still open the thread to see what their child has been
 * asking, the same read-only relationship STUDENT has to the PARENT-sent
 * message thread in MessageService, just mirrored.
 */
@Service
public class DoubtService {

    private static final Logger log = LoggerFactory.getLogger(DoubtService.class);

    // Doubt-solving calls hit a paid AI provider - this cap exists purely
    // to stop one runaway client (buggy retry loop, or a student mashing
    // "ask") from generating unbounded provider cost, not as a product
    // feature. Resets at midnight India time regardless of which tenant/
    // timezone the school itself operates in - see class javadoc, this
    // whole app already assumes Asia/Kolkata (see format.ts on the
    // frontend using en-IN throughout).
    private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Kolkata");

    private final DoubtMessageRepository doubtMessageRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StudentRepository studentRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AiTutorClient aiTutorClient;
    private final int dailyQuestionLimit;

    public DoubtService(
            DoubtMessageRepository doubtMessageRepository,
            StudentAccessGuard studentAccessGuard,
            StudentRepository studentRepository,
            CurrentTenantProvider currentTenantProvider,
            AiTutorClient aiTutorClient,
            @Value("${app.ai.daily-question-limit:30}") int dailyQuestionLimit) {
        this.doubtMessageRepository = doubtMessageRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.studentRepository = studentRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.aiTutorClient = aiTutorClient;
        this.dailyQuestionLimit = dailyQuestionLimit;
    }

    public DoubtThreadResponse listThread(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        List<DoubtMessageResponse> messages = doubtMessageRepository
                .findByTenantIdAndStudentIdOrderByCreatedAtAsc(tenantId, studentId).stream()
                .map(this::toResponse)
                .toList();

        int askedToday = countAskedToday(tenantId, studentId);
        return new DoubtThreadResponse(messages, askedToday, dailyQuestionLimit);
    }

    @Transactional
    public AskDoubtResponse ask(UUID studentId, AskDoubtRequest request) {
        studentAccessGuard.assertCanView(studentId);
        String role = currentTenantProvider.getCurrentRole();
        if (!"STUDENT".equals(role)) {
            // A parent/staff member can read the thread (the guard above
            // let them this far) but can't ask on the student's behalf -
            // see class javadoc.
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_ASK",
                    "Only the student themselves can ask the AI doubt solver");
        }

        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        if (countAskedToday(tenantId, studentId) >= dailyQuestionLimit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DAILY_LIMIT_REACHED",
                    "You've reached today's limit of " + dailyQuestionLimit
                            + " questions. Please try again tomorrow.");
        }

        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        // History window is read BEFORE the new question is saved, so the
        // question the student just asked isn't duplicated into the
        // context sent to the AI provider (it's appended separately in
        // AnthropicAiTutorClient#buildRequestBody).
        List<Turn> history = doubtMessageRepository
                .findTop10ByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId).stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(m -> new Turn(m.getSender(), m.getBody()))
                .toList();

        DoubtMessage questionMessage = doubtMessageRepository.save(
                new DoubtMessage(tenantId, studentId, DoubtSender.STUDENT, request.question()));

        String answerText;
        try {
            answerText = aiTutorClient.answer(student.getGrade(), history, request.question());
        } catch (AiTutorException e) {
            // The student's question is already saved regardless - losing
            // the AI reply must never lose their question too. They see a
            // friendly apology in the thread and can just ask again,
            // rather than the request failing outright with nothing saved
            // (same "never let a side-effect failure break the primary
            // action" rule as TwilioSmsAlertSender).
            log.error("AI tutor failed to answer for student {}: {}", studentId, e.getMessage(), e);
            answerText = "Sorry, I couldn't come up with an answer just now. Please try asking again in a moment.";
        }

        DoubtMessage answerMessage = doubtMessageRepository.save(
                new DoubtMessage(tenantId, studentId, DoubtSender.AI, answerText));

        return new AskDoubtResponse(toResponse(questionMessage), toResponse(answerMessage));
    }

    private int countAskedToday(UUID tenantId, UUID studentId) {
        Instant startOfDay = LocalDate.now(RESET_ZONE).atStartOfDay(RESET_ZONE).toInstant();
        return (int) doubtMessageRepository.countByTenantIdAndStudentIdAndSenderAndCreatedAtGreaterThanEqual(
                tenantId, studentId, DoubtSender.STUDENT, startOfDay);
    }

    private DoubtMessageResponse toResponse(DoubtMessage m) {
        return new DoubtMessageResponse(m.getId(), m.getSender().name(), m.getBody(), m.getCreatedAt());
    }
}
