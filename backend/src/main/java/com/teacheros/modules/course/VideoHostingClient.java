package com.teacheros.modules.course;

import java.time.Duration;

/**
 * The management side of the video provider: create a video, sign a
 * direct-to-provider upload, read processing status, delete. Sits beside
 * VideoPlaybackSigner (the playback side) and is selected by the same
 * app.video.provider property. Swapping provider = one more implementation of
 * these two interfaces; no caller changes.
 *
 * Video bytes never pass through this application.
 */
public interface VideoHostingClient {

    /** Stored in lessons.video_provider / video_uploads.provider. */
    String providerName();

    /** Creates an empty video at the provider and returns the provider's reference. */
    String createVideo(String title);

    /**
     * Credentials the browser needs to upload the file straight to the
     * provider, or null when the provider needs no client-side upload (the dev
     * "log" provider). Never contains the provider's API key.
     */
    UploadCredentials signUpload(String ref, Duration ttl);

    VideoInfo getVideo(String ref);

    /** Idempotent: a video that is already gone is not an error. */
    void deleteVideo(String ref);

    record UploadCredentials(
            String tusEndpoint,
            String libraryId,
            String videoId,
            long authorizationExpire,
            String authorizationSignature) {
    }

    enum State {
        WAITING_FOR_FILE,
        PROCESSING,
        READY,
        FAILED
    }

    record VideoInfo(State state, long sizeBytes, Integer durationSeconds, String failureReason) {
    }
}
