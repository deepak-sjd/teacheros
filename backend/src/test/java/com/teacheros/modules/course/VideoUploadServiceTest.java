package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.course.VideoHostingClient.State;
import com.teacheros.modules.course.VideoHostingClient.VideoInfo;
import com.teacheros.modules.course.dto.VideoUploadDtos.CreateUploadRequest;
import com.teacheros.modules.course.dto.VideoUploadDtos.CreateUploadResponse;
import com.teacheros.modules.course.dto.VideoUploadDtos.VideoUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoUploadServiceTest {

    private static final long GB = 1024L * 1024L * 1024L;

    @Mock
    VideoUploadRepository uploadRepository;
    @Mock
    LessonRepository lessonRepository;
    @Mock
    VideoHostingClient hostingClient;
    @Mock
    AuditService auditService;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    private VideoUploadService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // 2 GB storage per tenant, 1 GB per video.
        service = new VideoUploadService(uploadRepository, lessonRepository, hostingClient, auditService,
                currentTenantProvider, 2, 1);
        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        lenient().when(currentTenantProvider.getCurrentUserId()).thenReturn(userId);
        lenient().when(hostingClient.providerName()).thenReturn("bunny");
        lenient().when(uploadRepository.save(any(VideoUpload.class))).thenAnswer(i -> {
            VideoUpload u = i.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            if (u.getCreatedAt() == null) {
                u.setCreatedAt(Instant.now());
            }
            return u;
        });
    }

    private VideoUpload upload(VideoUploadStatus status) {
        VideoUpload u = new VideoUpload(tenantId, "bunny", "ref-1", "Lecture 1", 500, userId);
        u.setId(UUID.randomUUID());
        u.setCreatedAt(Instant.now());
        u.setStatus(status);
        return u;
    }

    // ---- create ----

    @Test
    void create_overTheStorageCap_isRefusedBeforeAnythingIsCreated() {
        when(uploadRepository.sumActiveBytes(tenantId)).thenReturn(GB + GB / 2); // 1.5 GB used

        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(new CreateUploadRequest("Big", 700L * 1024 * 1024))); // +0.68 GB > 2 GB

        assertEquals("STORAGE_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("GB"), "message should say how much is used");
        verify(hostingClient, never()).createVideo(any());
        verify(uploadRepository, never()).save(any());
    }

    @Test
    void create_aSingleVideoOverThePerVideoMax_isRefused() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(new CreateUploadRequest("Huge", 2 * GB)));

        assertEquals("FILE_TOO_LARGE", ex.getCode());
        verify(hostingClient, never()).createVideo(any());
    }

    @Test
    void create_justUnderTheCap_succeedsAndReturnsUploadCredentials() {
        when(uploadRepository.sumActiveBytes(tenantId)).thenReturn(GB);
        when(hostingClient.createVideo("Lecture 1")).thenReturn("ref-1");
        when(hostingClient.signUpload(eq("ref-1"), any(Duration.class)))
                .thenReturn(new VideoHostingClient.UploadCredentials("https://tus.example", "123", "ref-1", 999L, "sig"));

        CreateUploadResponse response = service.create(new CreateUploadRequest("  Lecture 1  ", GB));

        assertEquals(VideoUploadStatus.PENDING_UPLOAD, response.upload().status());
        assertEquals("ref-1", response.upload().videoRef());
        assertNotNull(response.credentials());
        assertEquals("sig", response.credentials().authorizationSignature());
        assertEquals("ref-1", response.credentials().videoId());
        verify(auditService).record(eq(AuditActions.VIDEO_UPLOAD_CREATED), eq(AuditActions.VIDEO_UPLOAD), any(),
                anyMap());
    }

    @Test
    void create_withTheDevProvider_returnsNoCredentials() {
        when(hostingClient.createVideo(any())).thenReturn("dev-1");
        when(hostingClient.signUpload(any(), any())).thenReturn(null);

        CreateUploadResponse response = service.create(new CreateUploadRequest("Lecture", 100L));

        assertNull(response.credentials());
    }

    @Test
    void create_whenTheProviderIsDown_failsCleanlyAndSavesNothing() {
        when(hostingClient.createVideo(any())).thenThrow(new VideoProviderException("down", null));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(new CreateUploadRequest("Lecture", 100L)));

        assertEquals("VIDEO_PROVIDER_ERROR", ex.getCode());
        verify(uploadRepository, never()).save(any());
    }

    @Test
    void create_whenSavingFails_theProviderVideoIsCleanedUp() {
        when(hostingClient.createVideo(any())).thenReturn("ref-orphan");
        when(uploadRepository.save(any(VideoUpload.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> service.create(new CreateUploadRequest("Lecture", 100L)));

        verify(hostingClient).deleteVideo("ref-orphan");
    }

    // ---- refresh ----

    @Test
    void refresh_whenFinished_becomesReadyWithTheRealSizeAndDuration() {
        VideoUpload u = upload(VideoUploadStatus.PROCESSING);
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));
        when(hostingClient.getVideo("ref-1")).thenReturn(new VideoInfo(State.READY, 123_456L, 600, null));

        VideoUploadResponse response = service.refresh(u.getId());

        assertEquals(VideoUploadStatus.READY, response.status());
        assertEquals(123_456L, response.sizeBytes());
        assertEquals(600, response.durationSeconds());
    }

    @Test
    void refresh_whileEncoding_isProcessing() {
        VideoUpload u = upload(VideoUploadStatus.PENDING_UPLOAD);
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));
        when(hostingClient.getVideo("ref-1")).thenReturn(new VideoInfo(State.PROCESSING, 0, null, null));

        assertEquals(VideoUploadStatus.PROCESSING, service.refresh(u.getId()).status());
    }

    @Test
    void refresh_whenTheProviderFailedIt_isFailedWithAReason() {
        VideoUpload u = upload(VideoUploadStatus.PROCESSING);
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));
        when(hostingClient.getVideo("ref-1")).thenReturn(new VideoInfo(State.FAILED, 0, null, "Bad file"));

        VideoUploadResponse response = service.refresh(u.getId());

        assertEquals(VideoUploadStatus.FAILED, response.status());
        assertEquals("Bad file", response.failureReason());
    }

    @Test
    void refresh_anUploadNeverCompleted_failsAfterTheWindowAndFreesTheProviderVideo() {
        VideoUpload u = upload(VideoUploadStatus.PENDING_UPLOAD);
        u.setCreatedAt(Instant.now().minus(Duration.ofHours(25)));
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));
        when(hostingClient.getVideo("ref-1")).thenReturn(new VideoInfo(State.WAITING_FOR_FILE, 0, null, null));

        VideoUploadResponse response = service.refresh(u.getId());

        assertEquals(VideoUploadStatus.FAILED, response.status());
        verify(hostingClient).deleteVideo("ref-1");
    }

    @Test
    void refresh_aFinishedUpload_doesNotCallTheProviderAgain() {
        VideoUpload u = upload(VideoUploadStatus.READY);
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));

        service.refresh(u.getId());

        verify(hostingClient, never()).getVideo(any());
    }

    @Test
    void refresh_anotherTenantsVideo_isNotFound() {
        UUID id = UUID.randomUUID();
        when(uploadRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.refresh(id));
        verify(hostingClient, never()).getVideo(any());
    }

    // ---- delete ----

    @Test
    void delete_aVideoALessonStillUses_isRefused() {
        VideoUpload u = upload(VideoUploadStatus.READY);
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));
        when(lessonRepository.existsByTenantIdAndVideoProviderAndVideoRef(tenantId, "bunny", "ref-1"))
                .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.delete(u.getId()));

        assertEquals("VIDEO_IN_USE", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(hostingClient, never()).deleteVideo(any());
        verify(uploadRepository, never()).delete(any());
    }

    @Test
    void delete_unusedVideo_isRemovedFromTheProviderAndTheLibraryAndAudited() {
        VideoUpload u = upload(VideoUploadStatus.READY);
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));

        service.delete(u.getId());

        verify(hostingClient).deleteVideo("ref-1");
        verify(uploadRepository).delete(u);
        verify(auditService).record(eq(AuditActions.VIDEO_UPLOAD_DELETED), eq(AuditActions.VIDEO_UPLOAD),
                eq(u.getId()), anyMap());
    }

    @Test
    void delete_whenTheProviderFails_keepsTheRowSoStorageIsNotSilentlyLeaked() {
        VideoUpload u = upload(VideoUploadStatus.READY);
        when(uploadRepository.findByIdAndTenantId(u.getId(), tenantId)).thenReturn(Optional.of(u));
        doThrow(new VideoProviderException("down", null)).when(hostingClient).deleteVideo("ref-1");

        assertThrows(ApiException.class, () -> service.delete(u.getId()));

        verify(uploadRepository, never()).delete(any());
    }

    @Test
    void delete_anotherTenantsVideo_isNotFound() {
        UUID id = UUID.randomUUID();
        when(uploadRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(id));
        verify(hostingClient, never()).deleteVideo(any());
    }
}
