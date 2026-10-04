package com.teacheros.modules.user.dto;

import com.teacheros.modules.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class UserDtos {

    public record MyProfileResponse(
            UUID id,
            String phoneNumber,
            String displayName,
            String email,
            Role role,
            String tenantName) {
    }

    // phoneNumber is deliberately not editable here - it's the login
    // identity the OTP goes to, not plain contact info, so changing it
    // needs its own verify-the-new-number flow (not built yet) rather than
    // a plain profile edit that could lock someone out or let a typo hand
    // the account to a different phone number.
    public record UpdateProfileRequest(
            @NotBlank String displayName,
            @Email(message = "Enter a valid email address") String email) {
    }
}
