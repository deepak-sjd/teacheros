package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.course.dto.CourseContentDtos.SaveLessonRequest;
import com.teacheros.modules.course.dto.CourseContentDtos.StaffLesson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseContentServiceTest {

    @Mock
    CourseRepository courseRepository;
    @Mock
    ChapterRepository chapterRepository;
    @Mock
    LessonRepository lessonRepository;
    @Mock
    CurrentTenantProvider currentTenantProvider;
    @Mock
    VideoAttachmentGuard videoAttachmentGuard;

    private CourseContentService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CourseContentService(courseRepository, chapterRepository, lessonRepository,
                currentTenantProvider, videoAttachmentGuard);
        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(lessonRepository.save(any(Lesson.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void chapterExists() {
        Chapter ch = new Chapter(tenantId, courseId, "Ch 1", 0);
        ch.setId(chapterId);
        when(chapterRepository.findByIdAndTenantId(chapterId, tenantId)).thenReturn(Optional.of(ch));
        when(lessonRepository.findByTenantIdAndChapterIdOrderByPositionAsc(tenantId, chapterId))
                .thenReturn(List.of());
    }

    @Test
    void createLesson_videoWithoutReference_isRejected() {
        chapterExists();

        ApiException ex = assertThrows(ApiException.class, () -> service.createLesson(chapterId,
                new SaveLessonRequest("Intro", LessonType.VIDEO, "mux", " ", null, null, 60, null)));

        assertEquals("INVALID_LESSON", ex.getCode());
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void createLesson_linkWithoutHttpUrl_isRejected() {
        chapterExists();

        assertThrows(ApiException.class, () -> service.createLesson(chapterId,
                new SaveLessonRequest("Read", LessonType.LINK, null, null, null, "ftp://x", null, null)));
    }

    @Test
    void createLesson_notes_clearsVideoFieldsAndAppendsAtEnd() {
        chapterExists();

        StaffLesson lesson = service.createLesson(chapterId,
                new SaveLessonRequest("Notes", LessonType.NOTES, "mux", "stale", "Some text", null, 30, null));

        assertNull(lesson.videoProvider());
        assertNull(lesson.videoRef());
        assertNull(lesson.durationSeconds());
        assertEquals("Some text", lesson.notesText());
        assertEquals(0, lesson.position());
    }

    @Test
    void createLesson_inAnotherTenantsChapter_isNotFound() {
        when(chapterRepository.findByIdAndTenantId(chapterId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createLesson(chapterId,
                new SaveLessonRequest("Notes", LessonType.NOTES, null, null, "x", null, null, null)));
    }

    @Test
    void createLesson_video_isCheckedAgainstThisTenantsLibrary() {
        chapterExists();

        service.createLesson(chapterId,
                new SaveLessonRequest("Intro", LessonType.VIDEO, "bunny", "ref-1", null, null, 60, null));

        verify(videoAttachmentGuard).assertAttachable(tenantId, "bunny", "ref-1");
    }

    @Test
    void createLesson_videoThatIsNotAttachable_isRejectedAndNothingIsSaved() {
        chapterExists();
        doThrow(new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "UNKNOWN_VIDEO", "no"))
                .when(videoAttachmentGuard).assertAttachable(tenantId, "bunny", "someone-elses");

        ApiException ex = assertThrows(ApiException.class, () -> service.createLesson(chapterId,
                new SaveLessonRequest("Intro", LessonType.VIDEO, "bunny", "someone-elses", null, null, 60, null)));

        assertEquals("UNKNOWN_VIDEO", ex.getCode());
        verify(lessonRepository, never()).save(any());
    }
}
