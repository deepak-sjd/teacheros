package com.teacheros.modules.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ComplianceDtos {

    // state: PENDING (never asked) | GRANTED | WITHDRAWN
    public record ChildConsent(
            UUID studentId,
            String fullName,
            String state,
            String textVersion,
            Instant grantedAt,
            Instant withdrawnAt) {
    }

    public record ConsentStatusResponse(
            boolean consentRequired,
            String currentTextVersion,
            List<ChildConsent> children) {
    }

    // studentIds null = grant for every child that has never been asked;
    // an explicit list also re-grants children whose consent was withdrawn.
    public record GrantConsentRequest(
            @NotBlank String textVersion,
            List<UUID> studentIds) {
    }

    public record RequestErasureRequest(
            @Size(max = 1000) String reason) {
    }

    public record DecideErasureRequest(
            @Size(max = 1000) String note) {
    }

    public record ErasureRequestResponse(
            UUID id,
            UUID studentId,
            String studentName,
            String status,
            String requestedByRole,
            String reason,
            Instant createdAt,
            Instant decidedAt,
            String decisionNote,
            Instant completedAt,
            String failureReason) {
    }
}
