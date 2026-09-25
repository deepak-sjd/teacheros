package com.teacheros.modules.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public class StudentDtos {

    public record CreateStudentRequest(
            @NotBlank String fullName,
            String grade,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid guardian phone number") String guardianPhone,

            // Optional - only set this if the student has their own phone
            // and should be able to log in and view their own data directly.
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid phone number") String phoneNumber,

            // Optional - lets low-attendance/fee alerts also go out by email.
            @jakarta.validation.constraints.Email(message = "Enter a valid email address") String guardianEmail,

            UUID batchId) {
    }

    public record UpdateStudentRequest(
            @NotBlank String fullName,
            String grade,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid guardian phone number") String guardianPhone,
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid phone number") String phoneNumber,
            @jakarta.validation.constraints.Email(message = "Enter a valid email address") String guardianEmail,
            UUID batchId) {
    }

    public record StudentResponse(
            UUID id,
            String fullName,
            String grade,
            String guardianPhone,
            String phoneNumber,
            String guardianEmail,
            UUID batchId) {
    }
}
