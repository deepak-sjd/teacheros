package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Decides whether a lesson may point at (provider, ref). Without this, staff
 * could type any reference - including another tenant's video id - into a
 * lesson and have it signed for their own students.
 *
 * Rule: the video must be a READY upload belonging to THIS tenant. The only
 * exception is the dev "log" provider, where there is no real library.
 */
@Component
public class VideoAttachmentGuard {

    private final VideoUploadRepository uploadRepository;
    private final VideoHostingClient hostingClient;

    public VideoAttachmentGuard(VideoUploadRepository uploadRepository, VideoHostingClient hostingClient) {
        this.uploadRepository = uploadRepository;
        this.hostingClient = hostingClient;
    }

    public void assertAttachable(UUID tenantId, String provider, String ref) {
        Optional<VideoUpload> upload = uploadRepository.findByTenantIdAndProviderAndVideoRef(tenantId, provider, ref);
        if (upload.isPresent()) {
            if (upload.get().getStatus() != VideoUploadStatus.READY) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VIDEO_NOT_READY",
                        "That video is still processing. Try again once it shows as Ready.");
            }
            return;
        }
        if ("log".equals(hostingClient.providerName())) {
            return;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_VIDEO",
                "Choose a video from your Video Library. Upload it there first if it isn't listed.");
    }
}
