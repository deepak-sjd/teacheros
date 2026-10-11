package com.teacheros.modules.announcement.dto;

import com.teacheros.modules.announcement.AnnouncementAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class AnnouncementDtos {

    // batchId is required when audience == BATCH, grade when audience == CLASS;
    // both are ignored for ALL. Cross-field rules live in the service.
    public record CreateAnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String body,
            @NotNull AnnouncementAudience audience,
            UUID batchId,
            @Size(max = 50) String grade) {
    }

    public record AnnouncementResponse(
            UUID id,
            String title,
            String body,
            AnnouncementAudience audience,
            UUID batchId,
            String grade,
            String createdByName,
            Instant createdAt) {
    }

    /** Same fields as AnnouncementResponse plus whether the CALLER has read it. */
    public record FeedAnnouncementResponse(
            UUID id,
            String title,
            String body,
            AnnouncementAudience audience,
            UUID batchId,
            String grade,
            String createdByName,
            Instant createdAt,
            boolean read) {
    }

    /**
     * recipientCount = distinct phone numbers that could read this (guardian
     * phones of targeted students, plus a student's own phone if set).
     * readCount = how many of those have an account that has read it.
     */
    public record ReadStatsResponse(
            UUID announcementId,
            long readCount,
            long recipientCount) {
    }
}
