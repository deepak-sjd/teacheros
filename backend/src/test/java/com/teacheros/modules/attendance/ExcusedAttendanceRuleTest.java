package com.teacheros.modules.attendance;

import com.teacheros.modules.attendance.AttendanceRepository.AttendanceRecordRow;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the EXCUSED rule end to end against the real queries: an excused
 * record is neither attended nor counted in the denominator, so it never
 * lowers a student's percentage - but a genuine ABSENT still does.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ExcusedAttendanceRuleTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestEntityManager em;

    @Autowired
    AttendanceRepository attendanceRepository;

    private UUID tenantId;
    private UUID batchId;
    private UUID studentId;
    private UUID userId;
    private int dayOffset;

    @BeforeEach
    void setUp() {
        Tenant tenant = em.persistAndFlush(new Tenant("Test Tenant"));
        tenantId = tenant.getId();
        userId = em.persistAndFlush(new User(tenantId, "+919000000001", "Teacher", Role.OWNER)).getId();
        batchId = em.persistAndFlush(new Batch(tenantId, "Batch A", "Maths", null)).getId();
        studentId = em.persistAndFlush(new Student(tenantId, batchId, "Asha", "10", "+919000000002")).getId();
        dayOffset = 0;
    }

    private void mark(AttendanceStatus status) {
        LocalDate date = LocalDate.of(2026, 1, 1).plusDays(dayOffset++);
        ClassSession session = em.persistAndFlush(new ClassSession(tenantId, batchId, date));
        em.persistAndFlush(new Attendance(tenantId, session.getId(), studentId, status, userId));
    }

    private double percentage() {
        long recorded = attendanceRepository.countRecordedSessions(tenantId, studentId);
        long attended = attendanceRepository.countAttendedSessions(tenantId, studentId);
        return AttendancePercentageCalculator.calculate(recorded, attended);
    }

    @Test
    void excusedIsLeftOutOfTheDenominator() {
        mark(AttendanceStatus.PRESENT);
        mark(AttendanceStatus.PRESENT);
        mark(AttendanceStatus.EXCUSED);

        assertEquals(2, attendanceRepository.countRecordedSessions(tenantId, studentId));
        assertEquals(2, attendanceRepository.countAttendedSessions(tenantId, studentId));
        assertEquals(100.0, percentage());
    }

    @Test
    void excusedDoesNotHideARealAbsence() {
        mark(AttendanceStatus.PRESENT);
        mark(AttendanceStatus.PRESENT);
        mark(AttendanceStatus.ABSENT);
        mark(AttendanceStatus.EXCUSED);

        assertEquals(3, attendanceRepository.countRecordedSessions(tenantId, studentId));
        assertEquals(66.7, percentage());
    }

    @Test
    void lateStillCountsAsAttended() {
        mark(AttendanceStatus.LATE);
        mark(AttendanceStatus.ABSENT);

        assertEquals(50.0, percentage());
    }

    @Test
    void onlyExcusedRecordsGivesZeroNotAnError() {
        mark(AttendanceStatus.EXCUSED);
        mark(AttendanceStatus.EXCUSED);

        assertEquals(0, attendanceRepository.countRecordedSessions(tenantId, studentId));
        assertEquals(0.0, percentage());
    }

    @Test
    void batchRangeCountsUseTheSameRule() {
        mark(AttendanceStatus.PRESENT);
        mark(AttendanceStatus.ABSENT);
        mark(AttendanceStatus.EXCUSED);

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        assertEquals(2, attendanceRepository.countRecordedInRange(tenantId, batchId, from, to));
        assertEquals(1, attendanceRepository.countAttendedInRange(tenantId, batchId, from, to));
    }

    @Test
    void analyticsTrendRowsOmitExcused() {
        mark(AttendanceStatus.PRESENT);
        mark(AttendanceStatus.EXCUSED);
        mark(AttendanceStatus.ABSENT);

        List<AttendanceRecordRow> rows = attendanceRepository.findRecordsInRange(
                tenantId, batchId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(2, rows.size());
        assertTrue(rows.stream().noneMatch(r -> r.getStatus() == AttendanceStatus.EXCUSED));
    }
}
