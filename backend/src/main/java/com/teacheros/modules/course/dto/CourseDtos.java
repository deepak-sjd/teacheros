package com.teacheros.modules.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class CourseDtos {

    // ---- Staff ----

    /** Used for both create and full update. */
    public record SaveCourseRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 10000) String description,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @Size(max = 10000) String syllabus,
            @Size(max = 500) String demoVideoUrl,
            UUID batchId,
            boolean published) {
    }

    public record SetPublishedRequest(boolean published) {
    }

    public record CourseResponse(
            UUID id,
            String title,
            String description,
            BigDecimal price,
            String syllabus,
            String demoVideoUrl,
            UUID batchId,
            String batchName,
            boolean published,
            Instant createdAt,
            Instant updatedAt) {
    }

    // ---- Public (deliberately no batchId / published / timestamps) ----

    public record PublicSchoolResponse(String name, String logoUrl) {
    }

    public record PublicCourseSummary(
            UUID id,
            String title,
            String shortDescription,
            BigDecimal price,
            boolean hasDemoVideo) {
    }

    public record PublicCourseDetail(
            UUID id,
            String title,
            String description,
            BigDecimal price,
            String syllabus,
            String demoVideoUrl) {
    }
}
