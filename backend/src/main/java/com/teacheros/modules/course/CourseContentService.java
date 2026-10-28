package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.course.dto.CourseContentDtos.SaveChapterRequest;
import com.teacheros.modules.course.dto.CourseContentDtos.SaveLessonRequest;
import com.teacheros.modules.course.dto.CourseContentDtos.StaffChapter;
import com.teacheros.modules.course.dto.CourseContentDtos.StaffLesson;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff-side authoring of a course's chapters and lessons (staff-only at the
 * controller). Every lookup is tenant-scoped; the course/chapter an item is
 * attached to is always re-checked against the JWT's tenant.
 */
@Service
public class CourseContentService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final VideoAttachmentGuard videoAttachmentGuard;

    public CourseContentService(CourseRepository courseRepository, ChapterRepository chapterRepository,
            LessonRepository lessonRepository, CurrentTenantProvider currentTenantProvider,
            VideoAttachmentGuard videoAttachmentGuard) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.lessonRepository = lessonRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.videoAttachmentGuard = videoAttachmentGuard;
    }

    public List<StaffChapter> list(UUID courseId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        requireCourse(tenantId, courseId);

        Map<UUID, List<Lesson>> lessonsByChapter = lessonRepository.findByTenantIdAndCourseId(tenantId, courseId)
                .stream().collect(Collectors.groupingBy(Lesson::getChapterId));

        return chapterRepository.findByTenantIdAndCourseIdOrderByPositionAsc(tenantId, courseId).stream()
                .map(c -> toChapter(c, lessonsByChapter.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional
    public StaffChapter createChapter(UUID courseId, SaveChapterRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        requireCourse(tenantId, courseId);

        int next = chapterRepository.findByTenantIdAndCourseIdOrderByPositionAsc(tenantId, courseId).stream()
                .mapToInt(Chapter::getPosition).max().orElse(-1) + 1;
        Chapter chapter = new Chapter(tenantId, courseId, request.title().trim(),
                request.position() != null ? request.position() : next);
        return toChapter(chapterRepository.save(chapter), List.of());
    }

    @Transactional
    public StaffChapter updateChapter(UUID chapterId, SaveChapterRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Chapter chapter = requireChapter(tenantId, chapterId);
        chapter.setTitle(request.title().trim());
        if (request.position() != null) {
            chapter.setPosition(request.position());
        }
        Chapter saved = chapterRepository.save(chapter);
        return toChapter(saved, lessonRepository.findByTenantIdAndChapterIdOrderByPositionAsc(tenantId, chapterId));
    }

    @Transactional
    public void deleteChapter(UUID chapterId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        chapterRepository.delete(requireChapter(tenantId, chapterId)); // lessons/progress cascade in the DB
    }

    @Transactional
    public StaffLesson createLesson(UUID chapterId, SaveLessonRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Chapter chapter = requireChapter(tenantId, chapterId);

        int next = lessonRepository.findByTenantIdAndChapterIdOrderByPositionAsc(tenantId, chapterId).stream()
                .mapToInt(Lesson::getPosition).max().orElse(-1) + 1;
        Lesson lesson = new Lesson(tenantId, chapter.getCourseId(), chapterId, request.title().trim(),
                request.type(), request.position() != null ? request.position() : next);
        applyContent(lesson, request);
        return toLesson(lessonRepository.save(lesson));
    }

    @Transactional
    public StaffLesson updateLesson(UUID lessonId, SaveLessonRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Lesson lesson = requireLesson(tenantId, lessonId);
        lesson.setTitle(request.title().trim());
        lesson.setType(request.type());
        if (request.position() != null) {
            lesson.setPosition(request.position());
        }
        applyContent(lesson, request);
        return toLesson(lessonRepository.save(lesson));
    }

    @Transactional
    public void deleteLesson(UUID lessonId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        lessonRepository.delete(requireLesson(tenantId, lessonId)); // progress cascades in the DB
    }

    // ---- helpers ----

    /**
     * Sets exactly the fields the lesson's type uses and clears the rest, so
     * switching a lesson from VIDEO to NOTES can't leave a stale video
     * reference behind.
     */
    private void applyContent(Lesson lesson, SaveLessonRequest request) {
        lesson.setVideoProvider(null);
        lesson.setVideoRef(null);
        lesson.setNotesText(null);
        lesson.setLinkUrl(null);
        lesson.setDurationSeconds(request.durationSeconds());

        switch (request.type()) {
            case VIDEO -> {
                String provider = blankToNull(request.videoProvider());
                String ref = blankToNull(request.videoRef());
                if (provider == null || ref == null) {
                    throw invalid("A video lesson needs a video provider and a video reference.");
                }
                // The video must be a READY upload of THIS tenant (or the dev provider).
                videoAttachmentGuard.assertAttachable(lesson.getTenantId(), provider, ref);
                lesson.setVideoProvider(provider);
                lesson.setVideoRef(ref);
            }
            case NOTES -> {
                String notes = blankToNull(request.notesText());
                if (notes == null) {
                    throw invalid("A notes lesson needs some text.");
                }
                lesson.setNotesText(notes);
                lesson.setDurationSeconds(null);
            }
            case LINK -> {
                String link = blankToNull(request.linkUrl());
                if (link == null || !(link.startsWith("http://") || link.startsWith("https://"))) {
                    throw invalid("A link lesson needs a full URL starting with http:// or https://");
                }
                lesson.setLinkUrl(link);
                lesson.setDurationSeconds(null);
            }
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LESSON", message);
    }

    private Course requireCourse(UUID tenantId, UUID courseId) {
        return courseRepository.findByIdAndTenantId(courseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private Chapter requireChapter(UUID tenantId, UUID chapterId) {
        return chapterRepository.findByIdAndTenantId(chapterId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter", chapterId));
    }

    private Lesson requireLesson(UUID tenantId, UUID lessonId) {
        return lessonRepository.findByIdAndTenantId(lessonId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private StaffChapter toChapter(Chapter c, List<Lesson> lessons) {
        return new StaffChapter(c.getId(), c.getCourseId(), c.getTitle(), c.getPosition(),
                lessons.stream().sorted(Comparator.comparingInt(Lesson::getPosition)).map(this::toLesson).toList());
    }

    private StaffLesson toLesson(Lesson l) {
        return new StaffLesson(l.getId(), l.getChapterId(), l.getTitle(), l.getType(), l.getVideoProvider(),
                l.getVideoRef(), l.getNotesText(), l.getLinkUrl(), l.getDurationSeconds(), l.getPosition());
    }
}
