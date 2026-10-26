package com.teacheros.modules.academic.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public class AcademicDtos {

    // ---- Academic years ----

    public record CreateAcademicYearRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{4}-([0-9]{2}|[0-9]{4})$", message = "Use a format like 2026-27") String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            // true = make this the current year. The very first year is always current.
            Boolean current) {
    }

    public record UpdateAcademicYearRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{4}-([0-9]{2}|[0-9]{4})$", message = "Use a format like 2026-27") String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record AcademicYearResponse(
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean current) {
    }

    // ---- Classes ----

    public record CreateClassRequest(
            @NotBlank @Size(max = 50) String name,
            // null = put it after the existing classes
            Integer displayOrder) {
    }

    // Full replace. active null = unchanged.
    public record UpdateClassRequest(
            @NotBlank @Size(max = 50) String name,
            Integer displayOrder,
            Boolean active) {
    }

    public record ClassResponse(
            UUID id,
            String name,
            int displayOrder,
            boolean active) {
    }

    // ---- Sections ----

    public record CreateSectionRequest(
            @NotNull UUID classId,
            @NotBlank @Size(max = 20) String name,
            UUID classTeacherUserId,
            @Min(1) Integer capacity) {
    }

    // Full replace: a null classTeacherUserId clears the class teacher and a
    // null capacity removes the limit. active null = unchanged.
    public record UpdateSectionRequest(
            @NotBlank @Size(max = 20) String name,
            UUID classTeacherUserId,
            @Min(1) Integer capacity,
            Boolean active) {
    }

    public record SectionResponse(
            UUID id,
            UUID classId,
            String className,
            String name,
            UUID classTeacherUserId,
            String classTeacherName,
            Integer capacity,
            long studentCount,
            boolean active) {
    }

    // ---- Subjects and subject teachers ----

    public record CreateSubjectRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20) String code) {
    }

    // Full replace. active null = unchanged.
    public record UpdateSubjectRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20) String code,
            Boolean active) {
    }

    public record SubjectResponse(
            UUID id,
            String name,
            String code,
            boolean active) {
    }

    public record AssignTeacherRequest(
            @NotNull UUID teacherUserId) {
    }

    public record SubjectTeacherResponse(
            UUID subjectId,
            String subjectName,
            String subjectCode,
            UUID teacherUserId,
            String teacherName) {
    }

    // ---- Student placement ----

    public record PlaceStudentRequest(
            @NotNull UUID sectionId,
            @Size(max = 20) String rollNumber,
            // null = the tenant's current academic year
            UUID academicYearId) {
    }

    public record StudentPlacementResponse(
            UUID studentId,
            String fullName,
            UUID sectionId,
            String sectionName,
            UUID classId,
            String className,
            UUID academicYearId,
            String academicYearName,
            String rollNumber) {
    }
}
