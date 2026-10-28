package com.teacheros.modules.staff.dto;

import com.teacheros.modules.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class StaffDtos {

    // role is a String (not the Role enum) so an unknown value becomes a clean
    // 400 from StaffService instead of a JSON-parse failure.
    public record CreateStaffRequest(
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid phone number") String phoneNumber,
            @NotBlank String role) {
    }

    public record ChangeStaffRoleRequest(@NotBlank String role) {
    }

    public record StaffResponse(
            UUID id,
            String displayName,
            String phoneNumber,
            Role role,
            boolean active,
            Instant lastLoginAt,
            Instant createdAt) {
    }
}
