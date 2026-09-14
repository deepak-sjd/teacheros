package com.teacheros.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{10,15}$")
        String phoneNumber,

        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
        String code,

        // Only used the very first time a phone number logs in (signup).
        // Ignored on subsequent logins for an already-existing account.
        String displayName,
        String tenantName
) {
}
