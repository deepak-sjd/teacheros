package com.teacheros.modules.leave;

import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves tenant B can never read tenant A's leave requests through any
 * repository finder, and that the new migration (V121) matches the entity.
 * @DataJpaTest (not @SpringBootTest) so the S3/MinIO startup runner isn't
 * pulled in; Flyway still runs every migration.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LeaveRequestTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestEntityManager em;
    @Autowired LeaveRequestRepository leaves;

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Test
    void anotherTenantCanNeverSeeALeaveRequest() {
        Tenant a = em.persistAndFlush(new Tenant("Tenant A"));
        Tenant b = em.persistAndFlush(new Tenant("Tenant B"));
        User parentA = em.persistAndFlush(new User(a.getId(), "+911000000001", "Parent A", Role.PARENT));
        Batch batchA = em.persistAndFlush(new Batch(a.getId(), "Batch A", "Maths", null));
        Student studentA = em.persistAndFlush(new Student(a.getId(), batchA.getId(), "Asha", "8", "+911000000001"));

        LeaveRequest leave = leaves.saveAndFlush(
                new LeaveRequest(a.getId(), studentA.getId(), DAY, DAY.plusDays(1), "Fever", parentA.getId()));
        leave.setStatus(LeaveStatus.APPROVED);
        leaves.saveAndFlush(leave);

        PageRequest page = PageRequest.of(0, 10);

        // Tenant B, every finder.
        assertThat(leaves.findByIdAndTenantId(leave.getId(), b.getId())).isEmpty();
        assertThat(leaves.findForDecision(leave.getId(), b.getId())).isEmpty();
        assertThat(leaves.findByTenantIdAndStudentIdOrderByFromDateDescCreatedAtDesc(b.getId(), studentA.getId())).isEmpty();
        assertThat(leaves.findApprovedStudentIdsCovering(b.getId(), DAY, Set.of(studentA.getId()))).isEmpty();
        assertThat(leaves.existsByTenantIdAndStudentIdAndStatusInAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                b.getId(), studentA.getId(), List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), DAY.plusDays(1), DAY))
                .isFalse();

        // Tenant A, same finders see it.
        assertThat(leaves.findByIdAndTenantId(leave.getId(), a.getId())).isPresent();
        assertThat(leaves.findApprovedStudentIdsCovering(a.getId(), DAY, Set.of(studentA.getId())))
                .containsExactly(studentA.getId());
        assertThat(leaves.findPending(b.getId(), null, page)).isEmpty();
    }

    @Test
    void pendingListFiltersByBatchAndIgnoresDecidedRequests() {
        Tenant t = em.persistAndFlush(new Tenant("Tenant"));
        User parent = em.persistAndFlush(new User(t.getId(), "+912000000001", "Parent", Role.PARENT));
        Batch one = em.persistAndFlush(new Batch(t.getId(), "Batch 1", "Maths", null));
        Batch two = em.persistAndFlush(new Batch(t.getId(), "Batch 2", "Science", null));
        Student inOne = em.persistAndFlush(new Student(t.getId(), one.getId(), "Asha", "8", "+912000000001"));
        Student inTwo = em.persistAndFlush(new Student(t.getId(), two.getId(), "Ravi", "8", "+912000000002"));

        leaves.saveAndFlush(new LeaveRequest(t.getId(), inOne.getId(), DAY, DAY, "a", parent.getId()));
        leaves.saveAndFlush(new LeaveRequest(t.getId(), inTwo.getId(), DAY, DAY, "b", parent.getId()));
        LeaveRequest decided = new LeaveRequest(t.getId(), inOne.getId(), DAY.plusDays(5), DAY.plusDays(5), "c", parent.getId());
        decided.setStatus(LeaveStatus.REJECTED);
        leaves.saveAndFlush(decided);

        PageRequest page = PageRequest.of(0, 10);
        assertThat(leaves.findPending(t.getId(), null, page)).hasSize(2);
        assertThat(leaves.findPending(t.getId(), one.getId(), page).getContent())
                .extracting(LeaveRequest::getStudentId).containsExactly(inOne.getId());
    }

    @Test
    void aRejectedRequestDoesNotBlockAskingAgain() {
        Tenant t = em.persistAndFlush(new Tenant("Tenant"));
        User parent = em.persistAndFlush(new User(t.getId(), "+913000000001", "Parent", Role.PARENT));
        Student s = em.persistAndFlush(new Student(t.getId(), null, "Asha", "8", "+913000000001"));
        UUID studentId = s.getId();

        LeaveRequest rejected = new LeaveRequest(t.getId(), studentId, DAY, DAY.plusDays(2), "x", parent.getId());
        rejected.setStatus(LeaveStatus.REJECTED);
        leaves.saveAndFlush(rejected);

        assertThat(leaves.existsByTenantIdAndStudentIdAndStatusInAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                t.getId(), studentId, List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), DAY.plusDays(1), DAY))
                .isFalse();
    }
}
