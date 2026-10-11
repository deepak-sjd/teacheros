package com.teacheros.modules.assessment;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.security.AuthenticatedPrincipal;
import com.teacheros.modules.assessment.dto.AssessmentDtos.AssessmentResponse;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AssessmentDeleteAndVisibilityIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // StorageConfig's startup runner would otherwise try to reach MinIO.
    @MockitoBean
    S3Client s3Client;
    @MockitoBean
    S3Presigner s3Presigner;

    @Autowired
    AssessmentService assessmentService;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    BatchRepository batchRepository;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    AssessmentRepository assessmentRepository;
    @Autowired
    MarkRepository markRepository;
    @Autowired
    TestQuestionRepository questionRepository;
    @Autowired
    TestAttemptRepository attemptRepository;
    @Autowired
    TestAnswerRepository answerRepository;

    private static final AtomicLong PHONE = new AtomicLong(9_000_000_000L);

    record Fixture(UUID tenantId, UUID ownerId, UUID batchId, UUID studentId, UUID studentUserId) {
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Task 1: deletion ----------

    @Test
    void deletesOfflineTestWithMarks() {
        Fixture fx = fixture();
        Assessment test = saveTest(fx, "Offline", TestType.OFFLINE);
        markRepository.save(new Mark(fx.tenantId, test.getId(), fx.studentId, BigDecimal.valueOf(7)));

        actAs(fx.tenantId, fx.ownerId, "OWNER");
        assessmentService.delete(test.getId(), false);

        assertThat(assessmentRepository.findByIdAndTenantId(test.getId(), fx.tenantId)).isEmpty();
        assertThat(markRepository.findByTenantIdAndTestId(fx.tenantId, test.getId())).isEmpty();
    }

    @Test
    void deletesOnlineTestWithQuestionsAndInProgressAttemptWithoutConfirm() {
        Fixture fx = fixture();
        Assessment test = saveTest(fx, "Online", TestType.ONLINE_MCQ);
        saveQuestion(fx, test);
        attemptRepository.save(new TestAttempt(fx.tenantId, test.getId(), fx.studentId, Instant.now()));

        actAs(fx.tenantId, fx.ownerId, "OWNER");
        assessmentService.delete(test.getId(), false);

        assertNothingLeft(fx, test);
    }

    @Test
    void refusesToDeleteOnlineTestWithSubmittedAttemptUnlessConfirmed() {
        Fixture fx = fixture();
        Assessment test = saveTest(fx, "Online", TestType.ONLINE_MCQ);
        TestQuestion q = saveQuestion(fx, test);
        submittedAttempt(fx, test, q);

        actAs(fx.tenantId, fx.ownerId, "OWNER");

        assertThatThrownBy(() -> assessmentService.delete(test.getId(), false))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("TEST_HAS_SUBMISSIONS");
                });
        // Refusal must leave everything intact.
        assertThat(assessmentRepository.findByIdAndTenantId(test.getId(), fx.tenantId)).isPresent();
        assertThat(attemptRepository.findByTenantIdAndTestId(fx.tenantId, test.getId())).hasSize(1);

        assessmentService.delete(test.getId(), true);
        assertNothingLeft(fx, test);
    }

    @Test
    void cannotDeleteAnotherTenantsTest() {
        Fixture mine = fixture();
        Fixture theirs = fixture();
        Assessment theirTest = saveTest(theirs, "Theirs", TestType.OFFLINE);

        actAs(mine.tenantId, mine.ownerId, "OWNER");

        assertThatThrownBy(() -> assessmentService.delete(theirTest.getId(), true))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(assessmentRepository.findByIdAndTenantId(theirTest.getId(), theirs.tenantId)).isPresent();
    }

    // ---------- Task 2: student visibility ----------

    @Test
    void emptyOnlineTestIsHiddenFromStudentsButVisibleToStaff() {
        Fixture fx = fixture();
        saveTest(fx, "Offline", TestType.OFFLINE);
        Assessment withQuestions = saveTest(fx, "Online ready", TestType.ONLINE_MCQ);
        saveQuestion(fx, withQuestions);
        saveTest(fx, "Online draft", TestType.ONLINE_MCQ); // no questions

        actAs(fx.tenantId, fx.ownerId, "OWNER");
        Page<AssessmentResponse> staffView = assessmentService.listForBatch(fx.batchId, null, PageRequest.of(0, 10));
        assertThat(staffView.getTotalElements()).isEqualTo(3);

        actAs(fx.tenantId, fx.studentUserId, "STUDENT");
        Page<AssessmentResponse> studentView = assessmentService.listForBatch(fx.batchId, null, PageRequest.of(0, 10));
        assertThat(studentView.getTotalElements()).isEqualTo(2);
        assertThat(studentView.getContent()).extracting(AssessmentResponse::title)
                .containsExactlyInAnyOrder("Offline", "Online ready");

        Page<AssessmentResponse> onlineOnly = assessmentService.listForBatch(fx.batchId, TestType.ONLINE_MCQ,
                PageRequest.of(0, 10));
        assertThat(onlineOnly.getContent()).extracting(AssessmentResponse::title).containsExactly("Online ready");
    }

    @Test
    void studentPagingTotalsExcludeHiddenTests() {
        Fixture fx = fixture();
        for (int i = 0; i < 3; i++) {
            Assessment ready = saveTest(fx, "Ready " + i, TestType.ONLINE_MCQ);
            saveQuestion(fx, ready);
            saveTest(fx, "Draft " + i, TestType.ONLINE_MCQ);
        }

        actAs(fx.tenantId, fx.studentUserId, "STUDENT");
        Page<AssessmentResponse> page = assessmentService.listForBatch(fx.batchId, null, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    // ---------- helpers ----------

    private void assertNothingLeft(Fixture fx, Assessment test) {
        assertThat(assessmentRepository.findByIdAndTenantId(test.getId(), fx.tenantId)).isEmpty();
        assertThat(questionRepository.findByTenantIdAndTestIdOrderByPositionAsc(fx.tenantId, test.getId())).isEmpty();
        assertThat(attemptRepository.findByTenantIdAndTestId(fx.tenantId, test.getId())).isEmpty();
        assertThat(markRepository.findByTenantIdAndTestId(fx.tenantId, test.getId())).isEmpty();
    }

    private Fixture fixture() {
        Tenant tenant = tenantRepository.save(new Tenant("Test tenant " + UUID.randomUUID()));
        User owner = userRepository.save(new User(tenant.getId(), nextPhone(), "Owner", Role.OWNER));
        Batch batch = batchRepository.save(new Batch(tenant.getId(), "Batch", "Maths", null));

        String studentPhone = nextPhone();
        Student student = new Student(tenant.getId(), batch.getId(), "Student", "10", nextPhone());
        student.setPhoneNumber(studentPhone);
        student = studentRepository.save(student);
        User studentUser = userRepository.save(new User(tenant.getId(), studentPhone, "Student", Role.STUDENT));

        return new Fixture(tenant.getId(), owner.getId(), batch.getId(), student.getId(), studentUser.getId());
    }

    private Assessment saveTest(Fixture fx, String title, TestType type) {
        Assessment a = new Assessment(fx.tenantId, fx.batchId, title, "Maths", BigDecimal.TEN, LocalDate.now(),
                fx.ownerId);
        a.setTestType(type);
        return assessmentRepository.save(a);
    }

    private TestQuestion saveQuestion(Fixture fx, Assessment test) {
        return questionRepository.save(new TestQuestion(fx.tenantId, test.getId(), "Q?", "a", "b", "c", "d", "A",
                BigDecimal.ONE, 0));
    }

    private void submittedAttempt(Fixture fx, Assessment test, TestQuestion question) {
        TestAttempt attempt = new TestAttempt(fx.tenantId, test.getId(), fx.studentId, Instant.now());
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        attempt.setScore(BigDecimal.ONE);
        attempt = attemptRepository.save(attempt);
        answerRepository.save(new TestAnswer(fx.tenantId, attempt.getId(), question.getId(), "A", true,
                BigDecimal.ONE));
        markRepository.save(new Mark(fx.tenantId, test.getId(), fx.studentId, BigDecimal.ONE));
    }

    private void actAs(UUID tenantId, UUID userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedPrincipal(userId, tenantId, role), null, List.of()));
    }

    private static String nextPhone() {
        return "+91" + PHONE.incrementAndGet();
    }
}
