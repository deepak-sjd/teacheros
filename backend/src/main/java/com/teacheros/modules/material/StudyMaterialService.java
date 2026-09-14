package com.teacheros.modules.material;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchAccessGuard;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.material.dto.StudyMaterialDtos.DownloadUrlResponse;
import com.teacheros.modules.material.dto.StudyMaterialDtos.StudyMaterialResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final BatchRepository batchRepository;
    private final BatchAccessGuard batchAccessGuard;
    private final StorageService storageService;
    private final CurrentTenantProvider currentTenantProvider;
    private final long maxFileSizeBytes;

    public StudyMaterialService(
            StudyMaterialRepository studyMaterialRepository,
            BatchRepository batchRepository,
            BatchAccessGuard batchAccessGuard,
            StorageService storageService,
            CurrentTenantProvider currentTenantProvider,
            @Value("${app.storage.max-file-size-mb}") long maxFileSizeMb) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.batchRepository = batchRepository;
        this.batchAccessGuard = batchAccessGuard;
        this.storageService = storageService;
        this.currentTenantProvider = currentTenantProvider;
        this.maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
    }

    @Transactional
    public StudyMaterialResponse upload(UUID batchId, String title, String description, MultipartFile file) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to upload.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "That file is too large. Maximum size is " + (maxFileSizeBytes / (1024 * 1024)) + "MB.");
        }
        if (!AllowedContentTypes.isAllowed(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE",
                    "That file type isn't supported. Upload a PDF, Word document, or image.");
        }

        String objectKey = storageService.upload(tenantId, batchId, file);

        StudyMaterial material = new StudyMaterial(
                tenantId, batchId, title, description, objectKey,
                file.getOriginalFilename(), file.getContentType(), file.getSize(), userId);

        return toResponse(studyMaterialRepository.save(material));
    }

    @Transactional
    public void delete(UUID materialId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        StudyMaterial material = studyMaterialRepository.findByIdAndTenantId(materialId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Study material", materialId));

        // Delete the stored object first - if this fails, the DB record
        // (and thus the error) stays visible rather than silently orphaning
        // a file in storage with no reference to it anywhere.
        storageService.delete(material.getObjectKey());
        studyMaterialRepository.delete(material);
    }

    public DownloadUrlResponse getDownloadUrl(UUID materialId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        StudyMaterial material = studyMaterialRepository.findByIdAndTenantId(materialId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Study material", materialId));
        batchAccessGuard.assertCanView(material.getBatchId());

        String url = storageService.generateDownloadUrl(material.getObjectKey());
        return new DownloadUrlResponse(url, 15);
    }

    public Page<StudyMaterialResponse> listForBatch(UUID batchId, Pageable pageable) {
        batchAccessGuard.assertCanView(batchId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return studyMaterialRepository.findByTenantIdAndBatchIdOrderByCreatedAtDesc(tenantId, batchId, pageable)
                .map(this::toResponse);
    }

    private StudyMaterialResponse toResponse(StudyMaterial material) {
        return new StudyMaterialResponse(material.getId(), material.getBatchId(), material.getTitle(),
                material.getDescription(), material.getOriginalFilename(), material.getContentType(),
                material.getFileSizeBytes(), material.getCreatedAt());
    }
}
