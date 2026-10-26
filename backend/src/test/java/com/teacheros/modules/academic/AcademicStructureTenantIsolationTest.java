package com.teacheros.modules.academic;

import com.teacheros.modules.reportcard.ExamTerm;
import com.teacheros.modules.reportcard.ExamTermRepository;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant A's rows are invisible to tenant B's finders for every new table,
 * and the database itself enforces the one-current-year and roll-number
 * rules. Each constraint violation is its own test: Postgres aborts the
 * transaction after one, so nothing can follow it in the same test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AcademicStructureTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TenantRepository tenants;
    @Autowired
    UserRepository users;
    @Autowired
    StudentRepository students;
    @Autowired
    AcademicYearRepository years;
    @Autowired
    SchoolClassRepository classes;
    @Autowired
    SectionRepository sections;
    @Autowired
    SubjectRepository subjects;
    @Autowired
    SectionSubjectTeacherRepository assignments;
    @Autowired
    AcademicStudentRepository academicStudents;
    @Autowired
    ExamTermRepository examTerms;

    private static final LocalDate START = LocalDate.of(2026, 4, 1);
    private static final LocalDate END = LocalDate.of(2027, 3, 31);

    private UUID tenant(String name) {
        return tenants.save(new Tenant(name)).getId();
    }

    private AcademicYear year(UUID tenantId, String name, boolean current) {
        AcademicYear y = new AcademicYear(tenantId, name, START, END);
        y.setCurrent(current);
        return y;
    }

    private Section section(UUID tenantId, String className, String sectionName) {
        SchoolClass c = classes.save(new SchoolClass(tenantId, className, 1));
        return sections.save(new Section(tenantId, c.getId(), sectionName));
    }

    private Student placed(UUID tenantId, String name, UUID sectionId, UUID yearId, String roll) {
        Student s = new Student(tenantId, null, name, "8", "+919222222222");
        s.setSectionId(sectionId);
        s.setAcademicYearId(yearId);
        s.setRollNumber(roll);
        return s;
    }

    @Test
    void tenantBCannotSeeTenantAsAcademicStructure() {
        UUID a = tenant("Tenant A");
        UUID b = tenant("Tenant B");

        AcademicYear year = years.save(year(a, "2026-27", true));
        SchoolClass schoolClass = classes.save(new SchoolClass(a, "Class 8", 8));
        Section section = sections.save(new Section(a, schoolClass.getId(), "A"));
        Subject subject = subjects.save(new Subject(a, "Mathematics", "MATH"));
        UUID teacher = users.save(new User(a, "+919111111111", "Teacher A", Role.TEACHER)).getId();
        SectionSubjectTeacher link = assignments
                .save(new SectionSubjectTeacher(a, section.getId(), subject.getId(), teacher));
        Student student = students.save(placed(a, "Asha", section.getId(), year.getId(), "1"));

        // Tenant A sees its own rows...
        assertThat(years.findByIdAndTenantId(year.getId(), a)).isPresent();
        assertThat(years.findByTenantIdAndCurrentTrue(a)).isPresent();
        assertThat(classes.findByIdAndTenantId(schoolClass.getId(), a)).isPresent();
        assertThat(sections.findByIdAndTenantId(section.getId(), a)).isPresent();
        assertThat(subjects.findByIdAndTenantId(subject.getId(), a)).isPresent();
        assertThat(assignments.findByTenantIdAndSectionId(a, section.getId())).hasSize(1);
        assertThat(academicStudents.findByTenantIdAndSectionIdAndActiveTrue(a, section.getId())).hasSize(1);
        assertThat(academicStudents.countActiveBySection(a)).hasSize(1);

        // ...tenant B sees none of them.
        assertThat(years.findByIdAndTenantId(year.getId(), b)).isEmpty();
        assertThat(years.findByTenantIdOrderByStartDateDesc(b)).isEmpty();
        assertThat(years.findByTenantIdAndCurrentTrue(b)).isEmpty();
        assertThat(classes.findByIdAndTenantId(schoolClass.getId(), b)).isEmpty();
        assertThat(classes.findByTenantIdOrderByDisplayOrderAscNameAsc(b)).isEmpty();
        assertThat(classes.findByTenantIdAndIdIn(b, java.util.List.of(schoolClass.getId()))).isEmpty();
        assertThat(sections.findByIdAndTenantId(section.getId(), b)).isEmpty();
        assertThat(sections.findByTenantId(b)).isEmpty();
        assertThat(sections.findByTenantIdAndClassId(b, schoolClass.getId())).isEmpty();
        assertThat(subjects.findByIdAndTenantId(subject.getId(), b)).isEmpty();
        assertThat(subjects.findByTenantIdOrderByNameAsc(b)).isEmpty();
        assertThat(subjects.findByTenantIdAndIdIn(b, java.util.List.of(subject.getId()))).isEmpty();
        assertThat(assignments.findByTenantIdAndSectionId(b, section.getId())).isEmpty();
        assertThat(assignments.findByTenantIdAndSectionIdAndSubjectId(b, section.getId(), subject.getId())).isEmpty();
        assertThat(link.getTenantId()).isEqualTo(a);
        assertThat(academicStudents.findByIdAndTenantId(student.getId(), b)).isEmpty();
        assertThat(academicStudents.findByTenantIdAndSectionIdAndActiveTrue(b, section.getId())).isEmpty();
        assertThat(academicStudents.countByTenantIdAndSectionIdAndActiveTrue(b, section.getId())).isZero();
        assertThat(academicStudents.countActiveBySection(b)).isEmpty();
    }

    @Test
    void sameNamesAndOneCurrentYearEachAreAllowedInTwoTenants() {
        UUID a = tenant("T1");
        UUID b = tenant("T2");
        years.saveAndFlush(year(a, "2026-27", true));
        years.saveAndFlush(year(b, "2026-27", true));
        classes.saveAndFlush(new SchoolClass(a, "Class 8", 8));
        classes.saveAndFlush(new SchoolClass(b, "Class 8", 8));
        subjects.saveAndFlush(new Subject(a, "Maths", "MATH"));
        subjects.saveAndFlush(new Subject(b, "Maths", "MATH"));

        assertThat(years.findByTenantIdAndCurrentTrue(a)).isPresent();
        assertThat(years.findByTenantIdAndCurrentTrue(b)).isPresent();
        assertThat(classes.existsByTenantIdAndNameIgnoreCase(a, "class 8")).isTrue();
        assertThat(subjects.existsByTenantIdAndCodeIgnoreCase(b, "math")).isTrue();
    }

    @Test
    void twoCurrentYearsInOneTenantAreRejected() {
        UUID a = tenant("T1");
        years.saveAndFlush(year(a, "2025-26", true));

        assertThatThrownBy(() -> years.saveAndFlush(year(a, "2026-27", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameRollInSameSectionAndYearIsRejected() {
        UUID a = tenant("T1");
        AcademicYear y = years.save(year(a, "2026-27", true));
        Section s = section(a, "Class 8", "A");
        students.saveAndFlush(placed(a, "Asha", s.getId(), y.getId(), "7"));

        assertThatThrownBy(() -> students.saveAndFlush(placed(a, "Ravi", s.getId(), y.getId(), "7")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameRollInAnotherSectionOrYearIsAllowed() {
        UUID a = tenant("T1");
        AcademicYear y1 = years.save(year(a, "2025-26", false));
        AcademicYear y2 = years.save(year(a, "2026-27", true));
        Section sectionA = section(a, "Class 8", "A");
        Section sectionB = sections.save(new Section(a, sectionA.getClassId(), "B"));

        students.saveAndFlush(placed(a, "S1", sectionA.getId(), y1.getId(), "7"));
        students.saveAndFlush(placed(a, "S2", sectionA.getId(), y2.getId(), "7"));
        students.saveAndFlush(placed(a, "S3", sectionB.getId(), y1.getId(), "7"));

        assertThat(academicStudents.countByTenantIdAndSectionIdAndActiveTrue(a, sectionA.getId())).isEqualTo(2);
    }

    @Test
    void deactivatedStudentFreesTheirRollNumber() {
        UUID a = tenant("T1");
        AcademicYear y = years.save(year(a, "2026-27", true));
        Section s = section(a, "Class 8", "A");
        Student leaver = students.saveAndFlush(placed(a, "Asha", s.getId(), y.getId(), "7"));
        leaver.setActive(false);
        students.saveAndFlush(leaver);

        students.saveAndFlush(placed(a, "Ravi", s.getId(), y.getId(), "7"));

        assertThat(academicStudents.findByTenantIdAndSectionIdAndActiveTrue(a, s.getId())).hasSize(1);
    }

    @Test
    void examTermRejectsAnUnknownAcademicYear() {
        UUID a = tenant("T1");
        assertThatThrownBy(() -> examTerms.saveAndFlush(
                new ExamTerm(a, UUID.randomUUID(), "Term 1", START, START.plusDays(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void examTermAcceptsARealAcademicYearOrNone() {
        UUID a = tenant("T1");
        AcademicYear y = years.save(year(a, "2026-27", true));

        examTerms.saveAndFlush(new ExamTerm(a, y.getId(), "Term 1", START, START.plusDays(60)));
        examTerms.saveAndFlush(new ExamTerm(a, null, "Term 2", START.plusDays(90), START.plusDays(150)));

        assertThat(examTerms.count()).isEqualTo(2);
    }
}
