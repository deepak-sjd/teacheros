package com.teacheros.modules.course.dto;

import com.teacheros.modules.course.LessonType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public class CourseContentDtos {

    // ---- Staff authoring ----

    // position null = append at the end (create) / leave unchanged (update).
    public record SaveChapterRequest(
            @NotBlank @Size(max = 255) String title,
            @Min(0) Integer position) {
    }

    // Which content fields are required depends on type - checked in
    // CourseContentService (VIDEO: provider+ref, NOTES: notesText, LINK: linkUrl).
    public record SaveLessonRequest(
            @NotBlank @Size(max = 255) String title,
            @NotNull LessonType type,
            @Size(max = 30) String videoProvider,
            @Size(max = 255) String videoRef,
            @Size(max = 20000) String notesText,
            @Size(max = 500) String linkUrl,
            @Min(0) Integer durationSeconds,
            @Min(0) Integer position) {
    }

    /** Staff view - includes the provider reference, which students never get. */
    public record StaffLesson(
            UUID id,
            UUID chapterId,
            String title,
            LessonType type,
            String videoProvider,
            String videoRef,
            String notesText,
            String linkUrl,
            Integer durationSeconds,
            int position) {
    }

    public record StaffChapter(
            UUID id,
            UUID courseId,
            String title,
            int position,
            List<StaffLesson> lessons) {
    }

    // ---- Student learning ----

    public record EnrolledCourseSummary(
            UUID courseId,
            String title,
            String description,
            int totalLessons,
            int completedLessons) {
    }

    /** No video reference here - playback goes through /playback only. */
    public record LessonSummary(
            UUID id,
            String title,
            LessonType type,
            Integer durationSeconds,
            int position,
            boolean completed,
            int positionSeconds) {
    }

    public record StudentChapter(
            UUID id,
            String title,
            int position,
            List<LessonSummary> lessons) {
    }

    public record LearnCourseResponse(
            UUID courseId,
            String title,
            String description,
            int totalLessons,
            int completedLessons,
            List<StudentChapter> chapters) {
    }

    public record LessonDetailResponse(
            UUID id,
            UUID courseId,
            UUID chapterId,
            String title,
            LessonType type,
            String notesText,
            String linkUrl,
            Integer durationSeconds,
            int positionSeconds,
            boolean completed) {
    }

    public record PlaybackResponse(
            String url,
            int expiresInMinutes) {
    }

    // Both optional: a null field is left unchanged on the stored row.
    public record SaveProgressRequest(
            @Min(0) @Max(86400) Integer positionSeconds,
            Boolean completed) {
    }

    public record ProgressResponse(
            UUID lessonId,
            int positionSeconds,
            boolean completed) {
    }
}
