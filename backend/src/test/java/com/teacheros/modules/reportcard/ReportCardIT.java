package com.teacheros.modules.reportcard;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.security.AuthenticatedPrincipal;
import com.teacheros.modules.assessment.Assessment;
import com.teacheros.modules.assessment.AssessmentRepository;
import com.teacheros.modules.assessment.Mark;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.report.ReportPdfService;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ExamTermRequest;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ExamTermResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.GradeBandInput;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.GradeBandResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.RemarkEntry;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ReplaceGradeScaleRequest;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.SaveRemarksRequest;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.TermResultResponse;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ReportCardIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    S3Client s3Client;
    @MockitoBean
    S3Presigner s3Presigner;

    @Autowired
    ExamTermService examTermService;
    @Autowired
    GradeScaleService gradeScaleService;
    @Autowired
    ReportCardService reportCardService;
    @Autowired
    ReportPdfService reportPdfService;
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

    private static final AtomicLong PHONE = new AtomicLong(8_000_000_000L);

    record Fixture(UUID tenantId, UUID ownerId, UUID batchId, UUID studentId) {
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ---------- grade scale ----------

    @Test
    void usesCbseDefaultsWhenNothingConfigured() {
        Fixture fx = fixture();
        actAsOwner(fx);

        var scale = gradeScaleService.get();
        assertThat(scale.usingDefaults()).isTrue();
        assertThat(scale.bands()).hasSize(9);

        List<GradeBandResponse> bands = scale.bands();
        assertThat(GradeScaleService.resolve(bands, 95).grade()).isEqualTo("A1");
        assertThat(GradeScaleService.resolve(bands, 90.5).grade()).isEqualTo("A2"); // no gap at 90-91
        assertThat(GradeScaleService.resolve(bands, 32.5).grade()).isEqualTo("E1");
        assertThat(GradeScaleService.resolve(bands, 0).grade()).isEqualTo("E2");
    }

    @Test
    void rejectsInvalidScalesAndAcceptsValidOnesThenResets() {
        Fixture fx = fixture();
        actAsOwner(fx);

        // overlap
        assertThatThrownBy(() -> gradeScaleService.replace(scale(
                band("A", 50, 100), band("B", 0, 60))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_GRADE_SCALE"));
        // doesn't start at 0
        assertThatThrownBy(() -> gradeScaleService.replace(scale(
                band("A", 50, 100), band("B", 10, 49))))
                .isInstanceOf(ApiException.class);
        // doesn't end at 100
        assertThatThrownBy(() -> gradeScaleService.replace(scale(
                band("A", 50, 90), band("B", 0, 49))))
                .isInstanceOf(ApiException.class);

        var saved = gradeScaleService.replace(scale(band("Pass", 40, 100), band("Fail", 0, 39)));
        assertThat(saved.usingDefaults()).isFalse();
        assertThat(saved.bands()).extracting(GradeBandResponse::grade).containsExactly("Pass", "Fail");

        assertThat(gradeScaleService.reset().usingDefaults()).isTrue();
    }

    // ---------- term results ----------

    @Test
    void termResultsAreComputedFromMarksInsideTheTermOnly() {
        Fixture fx = fixture();
        actAsOwner(fx);
        examTermService.create(new ExamTermRequest("Term 1", null, LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(10)));

        gradedTest(fx, "Maths", LocalDate.now(), 8); // 80%
        gradedTest(fx, "Maths", LocalDate.now().minusDays(1), 6); // 60%
        gradedTest(fx, "Science", LocalDate.now(), 9); // 90%
        gradedTest(fx, "Maths", LocalDate.now().minusDays(100), 1); // outside the term

        List<TermResultResponse> results = reportCardService.getStudentResults(fx.studentId);
        assertThat(results).hasSize(1);
        TermResultResponse r = results.get(0);

        assertThat(r.gradedEntries()).isEqualTo(3);
        assertThat(r.subjects()).extracting("subject").containsExactly("Maths", "Science");
        assertThat(r.subjects().get(0).averagePercentage()).isEqualTo(70.0);
        assertThat(r.subjects().get(0).grade()).isEqualTo("B1");
        assertThat(r.subjects().get(1).grade()).isEqualTo("A2");
        assertThat(r.overallPercentage()).isEqualTo(76.7);
        assertThat(r.overallGrade()).isEqualTo("B1");
    }

    @Test
    void rejectsOverlappingAndBackwardsTerms() {
        Fixture fx = fixture();
        actAsOwner(fx);
        examTermService.create(new ExamTermRequest("Term 1", null, LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 9, 30)));

        assertThatThrownBy(() -> examTermService.create(new ExamTermRequest("Overlap", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("TERM_OVERLAP"));
        assertThatThrownBy(() -> examTermService.create(new ExamTermRequest("Backwards", null,
                LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 1))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_TERM_DATES"));
    }

    // ---------- remarks ----------

    @Test
    void remarksUpsertAndBlankClears() {
        Fixture fx = fixture();
        actAsOwner(fx);
        ExamTermResponse term = examTermService.create(new ExamTermRequest("Term 1", null,
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(5)));

        reportCardService.saveRemarks(term.id(), remarks(fx.studentId, "  Good progress.  "));
        assertThat(reportCardService.getStudentResults(fx.studentId).get(0).teacherRemark())
                .isEqualTo("Good progress.");
        assertThat(reportCardService.listRemarks(term.id(), fx.batchId)).hasSize(1);

        reportCardService.saveRemarks(term.id(), remarks(fx.studentId, "Better."));
        assertThat(reportCardService.listRemarks(term.id(), fx.batchId).get(0).remark()).isEqualTo("Better.");

        reportCardService.saveRemarks(term.id(), remarks(fx.studentId, "   "));
        assertThat(reportCardService.listRemarks(term.id(), fx.batchId)).isEmpty();
        assertThat(reportCardService.getStudentResults(fx.studentId).get(0).teacherRemark()).isNull();
    }

    // ---------- tenant isolation (new tenant-owned tables) ----------

    @Test
    void tenantsCannotSeeOrTouchEachOthersTermsScalesOrRemarks() {
        Fixture a = fixture();
        Fixture b = fixture();

        actAsOwner(a);
        ExamTermResponse termA = examTermService.create(new ExamTermRequest("A term", null,
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(5)));
        reportCardService.saveRemarks(termA.id(), remarks(a.studentId, "A's remark"));
        gradeScaleService.replace(scale(band("Pass", 40, 100), band("Fail", 0, 39)));

        actAsOwner(b);
        assertThat(examTermService.list()).isEmpty();
        assertThat(gradeScaleService.get().usingDefaults()).isTrue(); // A's scale doesn't leak into B

        assertThatThrownBy(() -> reportCardService.saveRemarks(termA.id(), remarks(b.studentId, "x")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> reportCardService.listRemarks(termA.id(), b.batchId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> examTermService.delete(termA.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> reportCardService.getStudentResults(a.studentId))
                .isInstanceOf(ResourceNotFoundException.class);

        actAsOwner(a);
        assertThat(examTermService.list()).hasSize(1);
        assertThat(reportCardService.listRemarks(termA.id(), a.batchId)).hasSize(1);
    }

    // ---------- PDF ----------

    @Test
    void reportCardPdfStillWorksWithoutTermsAndWithTerms() {
        Fixture fx = fixture();
        actAsOwner(fx);
        gradedTest(fx, "Maths", LocalDate.now(), 8);

        byte[] noTerms = reportPdfService.generateStudentReportCardPdf(fx.studentId);
        assertThat(new String(noTerms, 0, 4)).isEqualTo("%PDF");

        ExamTermResponse term = examTermService.create(new ExamTermRequest("Term 1", null,
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(5)));
        reportCardService.saveRemarks(term.id(), remarks(fx.studentId, "Keep it up."));

        byte[] withTerms = reportPdfService.generateStudentReportCardPdf(fx.studentId);
        assertThat(new String(withTerms, 0, 4)).isEqualTo("%PDF");
        assertThat(withTerms.length).isGreaterThan(noTerms.length);
    }

    // ---------- helpers ----------

    private ReplaceGradeScaleRequest scale(GradeBandInput... bands) {
        return new ReplaceGradeScaleRequest(List.of(bands));
    }

    private GradeBandInput band(String grade, int min, int max) {
        return new GradeBandInput(grade, BigDecimal.valueOf(min), BigDecimal.valueOf(max), null);
    }

    private SaveRemarksRequest remarks(UUID studentId, String text) {
        return new SaveRemarksRequest(List.of(new RemarkEntry(studentId, text)));
    }

    private void gradedTest(Fixture fx, String subject, LocalDate date, int marksOutOfTen) {
        Assessment a = assessmentRepository.save(new Assessment(fx.tenantId, fx.batchId, subject + " test",
                subject, BigDecimal.TEN, date, fx.ownerId));
        markRepository.save(new Mark(fx.tenantId, a.getId(), fx.studentId, BigDecimal.valueOf(marksOutOfTen)));
    }

    private Fixture fixture() {
        Tenant tenant = tenantRepository.save(new Tenant("Test tenant " + UUID.randomUUID()));
        User owner = userRepository.save(new User(tenant.getId(), nextPhone(), "Owner", Role.OWNER));
        Batch batch = batchRepository.save(new Batch(tenant.getId(), "Batch", "Maths", null));
        Student student = studentRepository.save(new Student(tenant.getId(), batch.getId(), "Student", "10",
                nextPhone()));
        return new Fixture(tenant.getId(), owner.getId(), batch.getId(), student.getId());
    }

    private void actAsOwner(Fixture fx) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedPrincipal(fx.ownerId, fx.tenantId, "OWNER"), null, List.of()));
    }

    private static String nextPhone() {
        return "+91" + PHONE.incrementAndGet();
    }
}
