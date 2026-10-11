package com.teacheros.modules.course;

import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.tenant.Tenant;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves chapters, lessons and lesson_progress can't leak across tenants at
 * the repository layer: every finder returns nothing for another tenant's ID
 * even though the rows exist. Runs the real Flyway migrations (V72 included)
 * against a real Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CourseContentTenantIsolationTest {

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
    ChapterRepository chapterRepository;
    @Autowired
    LessonRepository lessonRepository;
    @Autowired
    LessonProgressRepository progressRepository;

    @Test
    void courseContentIsInvisibleToOtherTenants() {
        Tenant a = em.persist(new Tenant("Tenant A"));
        Tenant b = em.persist(new Tenant("Tenant B"));
        Batch batch = em.persist(new Batch(a.getId(), "Batch", "Maths", null));
        Student student = em.persist(new Student(a.getId(), batch.getId(), "Asha", "8", "+919000000002"));
        Course course = em.persist(new Course(a.getId(), "Algebra", BigDecimal.ZERO));
        Chapter chapter = em.persist(new Chapter(a.getId(), course.getId(), "Ch 1", 0));
        Lesson lesson = em.persist(new Lesson(a.getId(), course.getId(), chapter.getId(), "L1",
                LessonType.NOTES, 0));
        LessonProgress progress = new LessonProgress(a.getId(), lesson.getId(), student.getId());
        em.persistAndFlush(progress);

        // Sanity: the owning tenant sees everything.
        assertTrue(chapterRepository.findByIdAndTenantId(chapter.getId(), a.getId()).isPresent());
        assertEquals(1, chapterRepository
                .findByTenantIdAndCourseIdOrderByPositionAsc(a.getId(), course.getId()).size());
        assertTrue(lessonRepository.findByIdAndTenantId(lesson.getId(), a.getId()).isPresent());
        assertEquals(1, lessonRepository.findByTenantIdAndCourseId(a.getId(), course.getId()).size());
        assertTrue(progressRepository
                .findByTenantIdAndLessonIdAndStudentId(a.getId(), lesson.getId(), student.getId()).isPresent());

        // Another tenant sees nothing, through every finder.
        assertTrue(chapterRepository.findByIdAndTenantId(chapter.getId(), b.getId()).isEmpty());
        assertTrue(chapterRepository
                .findByTenantIdAndCourseIdOrderByPositionAsc(b.getId(), course.getId()).isEmpty());
        assertTrue(lessonRepository.findByIdAndTenantId(lesson.getId(), b.getId()).isEmpty());
        assertTrue(lessonRepository.findByTenantIdAndCourseId(b.getId(), course.getId()).isEmpty());
        assertTrue(lessonRepository
                .findByTenantIdAndChapterIdOrderByPositionAsc(b.getId(), chapter.getId()).isEmpty());
        assertTrue(progressRepository
                .findByTenantIdAndLessonIdAndStudentId(b.getId(), lesson.getId(), student.getId()).isEmpty());
        assertTrue(progressRepository.findByTenantIdAndStudentIdAndLessonIdIn(
                b.getId(), student.getId(), List.of(lesson.getId())).isEmpty());
    }
}
