package com.teacheros.modules.course.dto;

import com.teacheros.modules.course.VideoUploadStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class VideoUploadDtos {

    public record CreateUploadRequest(
            @NotBlank @Size(max = 255) String title,
            // What the browser says it is about to send. Used for the storage
            // cap check up front; replaced by the provider's real size later.
            @NotNull @Min(1) Long fileSizeBytes) {
    }

    /** Staff-only: includes the provider reference, which students never get. */
    public record VideoUploadResponse(
            UUID id,
            String title,
            VideoUploadStatus status,
            long sizeBytes,
            Integer durationSeconds,
            String failureReason,
            String provider,
            String videoRef,
            Instant createdAt) {
    }

    /**
     * What the browser needs to upload straight to the provider. Never the
     * provider's API key - authorizationSignature is a one-way hash.
     */
    public record UploadCredentials(
            String tusEndpoint,
            String libraryId,
            String videoId,
            long authorizationExpire,
            String authorizationSignature) {
    }

    // credentials is null for the dev "log" provider: nothing to upload.
    public record CreateUploadResponse(
            VideoUploadResponse upload,
            UploadCredentials credentials) {
    }

    public record VideoLibraryResponse(
            long usedBytes,
            long limitBytes,
            List<VideoUploadResponse> uploads) {
    }
}
