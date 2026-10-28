package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.course.VideoHostingClient.VideoInfo;
import com.teacheros.modules.course.dto.VideoUploadDtos.CreateUploadRequest;
import com.teacheros.modules.course.dto.VideoUploadDtos.CreateUploadResponse;
import com.teacheros.modules.course.dto.VideoUploadDtos.UploadCredentials;
import com.teacheros.modules.course.dto.VideoUploadDtos.VideoLibraryResponse;
import com.teacheros.modules.course.dto.VideoUploadDtos.VideoUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Staff-side video library: start an upload, track processing, delete.
 * Staff-only at the controller; tenant always from the JWT.
 *
 * Deliberately NOT @Transactional: a provider call must never sit inside a
 * database transaction. Each repository call commits on its own, and a
 * failure after the provider video exists is cleaned up best-effort.
 *
 * Storage cap: declared size + current usage is checked before anything is
 * created. The declared size is the client's word, so once the provider
 * reports the real size (refresh) it replaces the declared one and counts
 * against the next upload. A client that understates its file can therefore
 * overshoot the cap by at most one upload, never repeatedly.
 */
@Service
public class VideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadService.class);

    // Also the lifetime of the upload signature handed to the browser.
    static final Duration UPLOAD_WINDOW = Duration.ofHours(24);

    private final VideoUploadRepository uploadRepository;
    private final LessonRepository lessonRepository;
    private final VideoHostingClient hostingClient;
    private final AuditService auditService;
    private final CurrentTenantProvider currentTenantProvider;
    private final long maxStorageBytes;
    private final long maxUploadBytes;

    public VideoUploadService(VideoUploadRepository uploadRepository, LessonRepository lessonRepository,
            VideoHostingClient hostingClient, AuditService auditService, CurrentTenantProvider currentTenantProvider,
            @Value("${app.video.max-storage-gb-per-tenant:50}") long maxStorageGb,
            @Value("${app.video.max-upload-gb:5}") long maxUploadGb) {
        this.uploadRepository = uploadRepository;
        this.lessonRepository = lessonRepository;
        this.hostingClient = hostingClient;
        this.auditService = auditService;
        this.currentTenantProvider = currentTenantProvider;
        this.maxStorageBytes = maxStorageGb * 1024L * 1024L * 1024L;
        this.maxUploadBytes = maxUploadGb * 1024L * 1024L * 1024L;
    }

    public VideoLibraryResponse library() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        List<VideoUploadResponse> uploads = uploadRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse).toList();
        return new VideoLibraryResponse(uploadRepository.sumActiveBytes(tenantId), maxStorageBytes, uploads);
    }

    public CreateUploadResponse create(CreateUploadRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();
        String title = request.title().trim();
        long size = request.fileSizeBytes();

        if (size > maxUploadBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "That video is too large. The maximum for one video is " + formatBytes(maxUploadBytes) + ".");
        }
        long used = uploadRepository.sumActiveBytes(tenantId);
        if (used + size > maxStorageBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STORAGE_LIMIT_EXCEEDED",
                    "Your video storage is full: " + formatBytes(used) + " of " + formatBytes(maxStorageBytes)
                            + " used. Delete videos you no longer need, or ask your administrator to raise the limit.");
        }

        String ref;
        try {
            ref = hostingClient.createVideo(title);
        } catch (VideoProviderException e) {
            throw providerDown(e);
        }

        VideoUpload saved;
        try {
            saved = uploadRepository.save(new VideoUpload(tenantId, hostingClient.providerName(), ref, title, size, userId));
        } catch (RuntimeException e) {
            deleteQuietly(ref); // don't leave an orphan the tenant can't see or delete
            throw e;
        }
        auditService.record(AuditActions.VIDEO_UPLOAD_CREATED, AuditActions.VIDEO_UPLOAD, saved.getId(),
                AuditService.meta("title", title, "sizeBytes", size));

        UploadCredentials credentials;
        try {
            credentials = toCredentials(hostingClient.signUpload(ref, UPLOAD_WINDOW));
        } catch (VideoProviderException e) {
            throw providerDown(e);
        }
        return new CreateUploadResponse(toResponse(saved), credentials);
    }

    /** Pulls the latest status from the provider. Safe to call repeatedly. */
    public VideoUploadResponse refresh(UUID uploadId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        VideoUpload upload = require(tenantId, uploadId);
        if (upload.getStatus() == VideoUploadStatus.READY || upload.getStatus() == VideoUploadStatus.FAILED) {
            return toResponse(upload);
        }

        VideoInfo info;
        try {
            info = hostingClient.getVideo(upload.getVideoRef());
        } catch (VideoProviderException e) {
            throw providerDown(e);
        }

        switch (info.state()) {
            case WAITING_FOR_FILE -> {
                if (upload.getCreatedAt().plus(UPLOAD_WINDOW).isBefore(Instant.now())) {
                    upload.setStatus(VideoUploadStatus.FAILED);
                    upload.setFailureReason("The upload was never completed. Please upload it again.");
                    deleteQuietly(upload.getVideoRef());
                } else {
                    upload.setStatus(VideoUploadStatus.PENDING_UPLOAD);
                }
            }
            case PROCESSING -> upload.setStatus(VideoUploadStatus.PROCESSING);
            case READY -> {
                upload.setStatus(VideoUploadStatus.READY);
                if (info.sizeBytes() > 0) {
                    upload.setSizeBytes(info.sizeBytes());
                }
                upload.setDurationSeconds(info.durationSeconds());
            }
            case FAILED -> {
                upload.setStatus(VideoUploadStatus.FAILED);
                upload.setFailureReason(info.failureReason() != null ? info.failureReason()
                        : "The video could not be processed.");
            }
        }
        return toResponse(uploadRepository.save(upload));
    }

    public void delete(UUID uploadId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        VideoUpload upload = require(tenantId, uploadId);

        if (lessonRepository.existsByTenantIdAndVideoProviderAndVideoRef(tenantId, upload.getProvider(),
                upload.getVideoRef())) {
            throw new ApiException(HttpStatus.CONFLICT, "VIDEO_IN_USE",
                    "A lesson is using this video. Remove it from the lesson first, then delete it.");
        }
        try {
            hostingClient.deleteVideo(upload.getVideoRef());
        } catch (VideoProviderException e) {
            throw providerDown(e); // keep the row: the video may still exist and cost storage
        }
        uploadRepository.delete(upload);
        auditService.record(AuditActions.VIDEO_UPLOAD_DELETED, AuditActions.VIDEO_UPLOAD, uploadId,
                AuditService.meta("title", upload.getTitle(), "sizeBytes", upload.getSizeBytes()));
    }

    // ---- helpers ----

    private VideoUpload require(UUID tenantId, UUID uploadId) {
        return uploadRepository.findByIdAndTenantId(uploadId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Video", uploadId));
    }

    private ApiException providerDown(VideoProviderException e) {
        log.error("Video provider call failed: {}", e.getMessage(), e);
        return new ApiException(HttpStatus.BAD_GATEWAY, "VIDEO_PROVIDER_ERROR",
                "The video service isn't responding. Please try again in a moment.");
    }

    private void deleteQuietly(String ref) {
        try {
            hostingClient.deleteVideo(ref);
        } catch (RuntimeException e) {
            log.warn("Could not clean up video {} at the provider: {}", ref, e.getMessage());
        }
    }

    private UploadCredentials toCredentials(VideoHostingClient.UploadCredentials c) {
        return c == null ? null : new UploadCredentials(c.tusEndpoint(), c.libraryId(), c.videoId(),
                c.authorizationExpire(), c.authorizationSignature());
    }

    private VideoUploadResponse toResponse(VideoUpload u) {
        return new VideoUploadResponse(u.getId(), u.getTitle(), u.getStatus(), u.getSizeBytes(),
                u.getDurationSeconds(), u.getFailureReason(), u.getProvider(), u.getVideoRef(), u.getCreatedAt());
    }

    static String formatBytes(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1) {
            return String.format("%.1f GB", gb);
        }
        return String.format("%.0f MB", bytes / (1024.0 * 1024.0));
    }
}
