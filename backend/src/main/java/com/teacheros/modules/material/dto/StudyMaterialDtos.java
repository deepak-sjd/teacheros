package com.teacheros.modules.material.dto;

import java.time.Instant;
import java.util.UUID;

public class StudyMaterialDtos {

    public record StudyMaterialResponse(
            UUID id,
            UUID batchId,
            String title,
            String description,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Instant createdAt) {
    }

    public record DownloadUrlResponse(
            String url,
            int expiresInMinutes) {
    }
}
