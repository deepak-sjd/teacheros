package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoAttachmentGuardTest {

    @Mock
    VideoUploadRepository uploadRepository;
    @Mock
    VideoHostingClient hostingClient;

    private final UUID tenantId = UUID.randomUUID();

    private VideoAttachmentGuard guard() {
        return new VideoAttachmentGuard(uploadRepository, hostingClient);
    }

    private VideoUpload upload(VideoUploadStatus status) {
        VideoUpload u = new VideoUpload(tenantId, "bunny", "ref-1", "Lecture 1", 100, UUID.randomUUID());
        u.setStatus(status);
        return u;
    }

    @Test
    void readyUploadOfThisTenant_isAttachable() {
        when(uploadRepository.findByTenantIdAndProviderAndVideoRef(tenantId, "bunny", "ref-1"))
                .thenReturn(Optional.of(upload(VideoUploadStatus.READY)));

        assertDoesNotThrow(() -> guard().assertAttachable(tenantId, "bunny", "ref-1"));
    }

    @Test
    void stillProcessing_isRefused() {
        when(uploadRepository.findByTenantIdAndProviderAndVideoRef(tenantId, "bunny", "ref-1"))
                .thenReturn(Optional.of(upload(VideoUploadStatus.PROCESSING)));

        ApiException ex = assertThrows(ApiException.class, () -> guard().assertAttachable(tenantId, "bunny", "ref-1"));
        assertEquals("VIDEO_NOT_READY", ex.getCode());
    }

    @Test
    void aReferenceThisTenantNeverUploaded_isRefused_evenIfItExistsForAnotherTenant() {
        // The finder is tenant-scoped, so another tenant's video is simply "not found" here.
        when(uploadRepository.findByTenantIdAndProviderAndVideoRef(tenantId, "bunny", "someone-elses"))
                .thenReturn(Optional.empty());
        when(hostingClient.providerName()).thenReturn("bunny");

        ApiException ex = assertThrows(ApiException.class,
                () -> guard().assertAttachable(tenantId, "bunny", "someone-elses"));
        assertEquals("UNKNOWN_VIDEO", ex.getCode());
    }

    @Test
    void devProvider_allowsAFreeFormReference() {
        when(uploadRepository.findByTenantIdAndProviderAndVideoRef(tenantId, "mux", "abc123"))
                .thenReturn(Optional.empty());
        when(hostingClient.providerName()).thenReturn("log");

        assertDoesNotThrow(() -> guard().assertAttachable(tenantId, "mux", "abc123"));
    }
}
