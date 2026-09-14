package com.teacheros.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RequestOtpRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid phone number")
        String phoneNumber
) {
}
