package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.course.dto.CourseContentDtos.LearnCourseResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.PlaybackResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.ProgressResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.SaveProgressRequest;
import com.teacheros.modules.student.StudentAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningServiceTest {

    @Mock
    CourseRepository courseRepository;
    @Mock
    ChapterRepository chapterRepository;
    @Mock
    LessonRepository lessonRepository;
    @Mock
    LessonProgressRepository progressRepository;
    @Mock
    StudentAccessGuard studentAccessGuard;
    @Mock
    CourseEnrollmentChecker enrollmentChecker;
    @Mock
    VideoPlaybackSigner playbackSigner;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    private LearningService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LearningService(courseRepository, chapterRepository, lessonRepository, progressRepository,
                studentAccessGuard, enrollmentChecker, playbackSigner, currentTenantProvider);
        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(studentAccessGuard.resolveCurrentStudentId()).thenReturn(studentId);
        lenient().when(progressRepository.save(any(LessonProgress.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Course course() {
        Course c = new Course(tenantId, "Algebra", BigDecimal.ZERO);
        c.setId(courseId);
        return c;
    }

    private Lesson lesson(LessonType type) {
        Lesson l = new Lesson(tenantId, courseId, chapterId, "Intro", type, 0);
        l.setId(lessonId);
        if (type == LessonType.VIDEO) {
            l.setVideoProvider("mux");
            l.setVideoRef("abc123");
        }
        return l;
    }

    private void enrolled(boolean value) {
        when(courseRepository.findByIdAndTenantId(courseId, tenantId)).thenReturn(Optional.of(course()));
        when(enrollmentChecker.canLearn(eq(tenantId), eq(studentId), any(Course.class))).thenReturn(value);
    }

    @Test
    void getCourse_whenNotEnrolled_isNotFound() {
        enrolled(false);

        assertThrows(ResourceNotFoundException.class, () -> service.getCourse(courseId));
        verify(lessonRepository, never()).findByTenantIdAndCourseId(any(), any());
    }

    @Test
    void getLesson_whenNotEnrolled_isNotFound() {
        when(lessonRepository.findByIdAndTenantId(lessonId, tenantId))
                .thenReturn(Optional.of(lesson(LessonType.NOTES)));
        enrolled(false);

        assertThrows(ResourceNotFoundException.class, () -> service.getLesson(lessonId));
    }

    @Test
    void getCourse_neverExposesVideoReference_andCountsCompleted() {
        Lesson video = lesson(LessonType.VIDEO);
        LessonProgress done = new LessonProgress(tenantId, lessonId, studentId);
        done.setCompleted(true);
        done.setPositionSeconds(42);

        enrolled(true);
        when(lessonRepository.findByTenantIdAndCourseId(tenantId, courseId)).thenReturn(List.of(video));
        when(progressRepository.findByTenantIdAndStudentIdAndLessonIdIn(eq(tenantId), eq(studentId), any()))
                .thenReturn(List.of(done));
        Chapter chapter = new Chapter(tenantId, courseId, "Ch 1", 0);
        chapter.setId(chapterId);
        when(chapterRepository.findByTenantIdAndCourseIdOrderByPositionAsc(tenantId, courseId))
                .thenReturn(List.of(chapter));

        LearnCourseResponse response = service.getCourse(courseId);

        assertEquals(1, response.totalLessons());
        assertEquals(1, response.completedLessons());
        assertEquals(42, response.chapters().get(0).lessons().get(0).positionSeconds());
        // LessonSummary has no videoRef/videoProvider field at all.
        assertTrue(java.util.Arrays.stream(
                com.teacheros.modules.course.dto.CourseContentDtos.LessonSummary.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().toLowerCase().contains("video")));
    }

    @Test
    void playback_forVideoLesson_returnsSignedUrl() {
        when(lessonRepository.findByIdAndTenantId(lessonId, tenantId))
                .thenReturn(Optional.of(lesson(LessonType.VIDEO)));
        enrolled(true);
        when(playbackSigner.signedPlaybackUrl(eq("mux"), eq("abc123"), any(Duration.class)))
                .thenReturn("https://signed.example/abc123?sig=1");

        PlaybackResponse response = service.getPlayback(lessonId);

        assertEquals("https://signed.example/abc123?sig=1", response.url());
        assertEquals(15, response.expiresInMinutes());
    }

    @Test
    void playback_forNonVideoLesson_isRejected() {
        when(lessonRepository.findByIdAndTenantId(lessonId, tenantId))
                .thenReturn(Optional.of(lesson(LessonType.NOTES)));
        enrolled(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.getPlayback(lessonId));

        assertEquals("NOT_A_VIDEO", ex.getCode());
    }

    @Test
    void saveProgress_createsRowAndMarksCompleted() {
        when(lessonRepository.findByIdAndTenantId(lessonId, tenantId))
                .thenReturn(Optional.of(lesson(LessonType.VIDEO)));
        enrolled(true);
        when(progressRepository.findByTenantIdAndLessonIdAndStudentId(tenantId, lessonId, studentId))
                .thenReturn(Optional.empty());

        ProgressResponse response = service.saveProgress(lessonId, new SaveProgressRequest(120, true));

        assertEquals(120, response.positionSeconds());
        assertTrue(response.completed());
    }

    @Test
    void saveProgress_withNullFields_keepsExistingValues() {
        LessonProgress existing = new LessonProgress(tenantId, lessonId, studentId);
        existing.setPositionSeconds(75);
        existing.setCompleted(true);

        when(lessonRepository.findByIdAndTenantId(lessonId, tenantId))
                .thenReturn(Optional.of(lesson(LessonType.VIDEO)));
        enrolled(true);
        when(progressRepository.findByTenantIdAndLessonIdAndStudentId(tenantId, lessonId, studentId))
                .thenReturn(Optional.of(existing));

        ProgressResponse response = service.saveProgress(lessonId, new SaveProgressRequest(null, null));

        assertEquals(75, response.positionSeconds());
        assertTrue(response.completed());
    }

    @Test
    void saveProgress_whenNotEnrolled_writesNothing() {
        when(lessonRepository.findByIdAndTenantId(lessonId, tenantId))
                .thenReturn(Optional.of(lesson(LessonType.VIDEO)));
        enrolled(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.saveProgress(lessonId, new SaveProgressRequest(10, false)));
        verify(progressRepository, never()).save(any());
        assertFalse(false);
    }
}
