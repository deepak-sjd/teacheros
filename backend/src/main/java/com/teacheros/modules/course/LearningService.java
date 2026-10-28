package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.course.dto.CourseContentDtos.EnrolledCourseSummary;
import com.teacheros.modules.course.dto.CourseContentDtos.LearnCourseResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.LessonDetailResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.LessonSummary;
import com.teacheros.modules.course.dto.CourseContentDtos.PlaybackResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.ProgressResponse;
import com.teacheros.modules.course.dto.CourseContentDtos.SaveProgressRequest;
import com.teacheros.modules.course.dto.CourseContentDtos.StudentChapter;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The student's side of a course: outline, lesson content, signed video
 * playback, and progress. STUDENT only (enforced at the controller); the
 * student always comes from the JWT via resolveCurrentStudentId, never from
 * the request.
 *
 * Every entry point funnels through requireEnrolledCourse, so a course (or a
 * lesson of one) the student isn't enrolled in is a 404 - it never confirms
 * the content exists.
 *
 * saveProgress() is deliberately NOT @Transactional: a player pings it often
 * and two pings can race on the unique key. Without a surrounding
 * transaction the loser's failed insert doesn't poison anything, and one
 * retry just updates the winner's row.
 */
@Service
public class LearningService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CourseEnrollmentChecker enrollmentChecker;
    private final VideoPlaybackSigner playbackSigner;
    private final CurrentTenantProvider currentTenantProvider;
    private final VideoPlaybackPolicy playbackPolicy;

    public LearningService(CourseRepository courseRepository, ChapterRepository chapterRepository,
            LessonRepository lessonRepository, LessonProgressRepository progressRepository,
            StudentAccessGuard studentAccessGuard, CourseEnrollmentChecker enrollmentChecker,
            VideoPlaybackSigner playbackSigner, CurrentTenantProvider currentTenantProvider,
            VideoPlaybackPolicy playbackPolicy) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.enrollmentChecker = enrollmentChecker;
        this.playbackSigner = playbackSigner;
        this.currentTenantProvider = currentTenantProvider;
        this.playbackPolicy = playbackPolicy;
    }

    public List<EnrolledCourseSummary> myCourses() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();

        return enrollmentChecker.coursesFor(tenantId, studentId).stream().map(c -> {
            List<Lesson> lessons = lessonRepository.findByTenantIdAndCourseId(tenantId, c.getId());
            int done = (int) progressFor(tenantId, studentId, lessons).values().stream()
                    .filter(LessonProgress::isCompleted).count();
            return new EnrolledCourseSummary(c.getId(), c.getTitle(), c.getDescription(), lessons.size(), done);
        }).toList();
    }

    public LearnCourseResponse getCourse(UUID courseId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();
        Course course = requireEnrolledCourse(tenantId, studentId, courseId);

        List<Lesson> lessons = lessonRepository.findByTenantIdAndCourseId(tenantId, courseId);
        Map<UUID, LessonProgress> progress = progressFor(tenantId, studentId, lessons);
        Map<UUID, List<Lesson>> byChapter = lessons.stream().collect(Collectors.groupingBy(Lesson::getChapterId));

        List<StudentChapter> chapters = chapterRepository
                .findByTenantIdAndCourseIdOrderByPositionAsc(tenantId, courseId).stream()
                .map(ch -> new StudentChapter(ch.getId(), ch.getTitle(), ch.getPosition(),
                        byChapter.getOrDefault(ch.getId(), List.of()).stream()
                                .sorted(Comparator.comparingInt(Lesson::getPosition))
                                .map(l -> toSummary(l, progress.get(l.getId())))
                                .toList()))
                .toList();

        int done = (int) progress.values().stream().filter(LessonProgress::isCompleted).count();
        return new LearnCourseResponse(course.getId(), course.getTitle(), course.getDescription(),
                lessons.size(), done, chapters);
    }

    public LessonDetailResponse getLesson(UUID lessonId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();
        Lesson lesson = requireLessonForStudent(tenantId, studentId, lessonId);

        LessonProgress p = progressRepository
                .findByTenantIdAndLessonIdAndStudentId(tenantId, lessonId, studentId).orElse(null);
        return new LessonDetailResponse(lesson.getId(), lesson.getCourseId(), lesson.getChapterId(),
                lesson.getTitle(), lesson.getType(), lesson.getNotesText(), lesson.getLinkUrl(),
                lesson.getDurationSeconds(), p != null ? p.getPositionSeconds() : 0,
                p != null && p.isCompleted());
    }

    /**
     * The only way a video reference ever leaves the server - as a short-lived
     * signed URL.
     */
    public PlaybackResponse getPlayback(UUID lessonId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();
        Lesson lesson = requireLessonForStudent(tenantId, studentId, lessonId);

        if (lesson.getType() != LessonType.VIDEO || lesson.getVideoRef() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_VIDEO", "This lesson isn't a video.");
        }
        // Only reached after requireLessonForStudent (enrolment) has passed.
        Duration ttl = playbackPolicy.ttlFor(lesson.getDurationSeconds());
        String url = playbackSigner.signedPlaybackUrl(lesson.getVideoProvider(), lesson.getVideoRef(), ttl);
        return new PlaybackResponse(url, (int) ttl.toMinutes());
    }

    public ProgressResponse saveProgress(UUID lessonId, SaveProgressRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID studentId = studentAccessGuard.resolveCurrentStudentId();
        requireLessonForStudent(tenantId, studentId, lessonId);

        try {
            return upsertProgress(tenantId, lessonId, studentId, request);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a simultaneous ping - the row exists now, so update it.
            return upsertProgress(tenantId, lessonId, studentId, request);
        }
    }

    // ---- helpers ----

    private ProgressResponse upsertProgress(UUID tenantId, UUID lessonId, UUID studentId,
            SaveProgressRequest request) {
        LessonProgress p = progressRepository.findByTenantIdAndLessonIdAndStudentId(tenantId, lessonId, studentId)
                .orElseGet(() -> new LessonProgress(tenantId, lessonId, studentId));

        if (request.positionSeconds() != null) {
            p.setPositionSeconds(request.positionSeconds());
        }
        if (request.completed() != null) {
            p.setCompleted(request.completed());
            p.setCompletedAt(request.completed()
                    ? (p.getCompletedAt() != null ? p.getCompletedAt() : Instant.now())
                    : null);
        }
        LessonProgress saved = progressRepository.save(p);
        return new ProgressResponse(lessonId, saved.getPositionSeconds(), saved.isCompleted());
    }

    private Course requireEnrolledCourse(UUID tenantId, UUID studentId, UUID courseId) {
        Course course = courseRepository.findByIdAndTenantId(courseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!enrollmentChecker.canLearn(tenantId, studentId, course)) {
            throw new ResourceNotFoundException("Course", courseId);
        }
        return course;
    }

    private Lesson requireLessonForStudent(UUID tenantId, UUID studentId, UUID lessonId) {
        Lesson lesson = lessonRepository.findByIdAndTenantId(lessonId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        try {
            requireEnrolledCourse(tenantId, studentId, lesson.getCourseId());
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        return lesson;
    }

    private Map<UUID, LessonProgress> progressFor(UUID tenantId, UUID studentId, Collection<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = lessons.stream().map(Lesson::getId).toList();
        return progressRepository.findByTenantIdAndStudentIdAndLessonIdIn(tenantId, studentId, ids).stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, Function.identity()));
    }

    private LessonSummary toSummary(Lesson l, LessonProgress p) {
        return new LessonSummary(l.getId(), l.getTitle(), l.getType(), l.getDurationSeconds(), l.getPosition(),
                p != null && p.isCompleted(), p != null ? p.getPositionSeconds() : 0);
    }
}
