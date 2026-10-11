package com.teacheros.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

/**
 * The only class in the application that talks directly to S3Client/
 * S3Presigner. Every feature that needs file storage (study material
 * today; homework attachments, profile pictures later) goes through this,
 * rather than each writing its own S3 calls - keeps the bucket-naming,
 * key-generation, and presigning logic in one place.
 */
@Service
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public StorageService(S3Client s3Client, S3Presigner s3Presigner,
            @Value("${app.storage.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    /**
     * Uploads a file under a namespaced key and returns that key for the
     * caller to store in their own table (e.g. StudyMaterial.objectKey).
     * The key is namespaced by tenant and batch so an object's location
     * alone reflects tenant isolation, even though the actual isolation
     * enforcement happens at the database/authorization layer, not here.
     */
    public String upload(UUID tenantId, UUID batchId, MultipartFile file) {
        String key = "tenants/%s/batches/%s/%s-%s".formatted(
                tenantId, batchId, UUID.randomUUID(), sanitizeFilename(file.getOriginalFilename()));

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        return key;
    }

    /**
     * For tenant-level assets with no batch to namespace under - currently
     * just the branding logo (see TenantService). Separate from upload()
     * rather than passing a fake/null batchId through it, since that method's
     * key format is specifically "tenants/{t}/batches/{b}/..." and a logo
     * isn't batch-scoped at all.
     */
    public String uploadTenantAsset(UUID tenantId, MultipartFile file) {
        String key = "tenants/%s/branding/%s-%s".formatted(
                tenantId, UUID.randomUUID(), sanitizeFilename(file.getOriginalFilename()));

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        return key;
    }

    /**
     * For per-student documents (birth certificate, transfer certificate,
     * ID proof...) - a student belongs to a tenant and may have no batch,
     * so this is namespaced by tenant and student instead of batch. Added
     * by Dev 13 (V120); existing upload methods are unchanged.
     */
    public String uploadStudentDocument(UUID tenantId, UUID studentId, MultipartFile file) {
        String key = "tenants/%s/students/%s/documents/%s-%s".formatted(
                tenantId, studentId, UUID.randomUUID(), sanitizeFilename(file.getOriginalFilename()));

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        return key;
    }

    /**
     * A short-lived, signed GET URL - never a permanent public link. Default
     * expiry is intentionally short; the client is expected to fetch a
     * fresh one each time it wants to download, not cache it long-term.
     */
    public String generateDownloadUrl(String objectKey) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build());
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        // Strip anything that isn't alphanumeric/dot/dash/underscore - the
        // filename ends up in an object key (and eventually a Content-
        // Disposition header), so this isn't just cosmetic.
        return original.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }
}
