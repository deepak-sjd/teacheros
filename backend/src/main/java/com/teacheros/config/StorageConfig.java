package com.teacheros.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

/**
 * Wires up an S3-compatible client. In dev/test this points at the local
 * MinIO container (see docker-compose.yml); in production it points at
 * whichever real provider is configured (AWS S3, Cloudflare R2,
 * DigitalOcean Spaces) - all speak the same S3 API, so nothing in the
 * application code needs to change when that decision is made, only these
 * three environment variables.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public S3Client s3Client(
            @Value("${app.storage.endpoint}") String endpoint,
            @Value("${app.storage.access-key}") String accessKey,
            @Value("${app.storage.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1) // MinIO ignores this; harmless placeholder for real S3-compatible providers
                                          // that require *a* region
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true) // required for MinIO and most S3-compatible providers (bucket-in-path, not
                                      // subdomain)
                .build();
    }

    /**
     * Ensures the configured bucket exists on startup - MinIO starts
     * completely empty, so without this the first real upload would fail
     * with "bucket does not exist" instead of working out of the box.
     * In production against a real provider, the bucket is typically
     * pre-provisioned via infra tooling rather than app-managed - this
     * check-then-create is a no-op (HeadBucket succeeds) once that's true,
     * so it's safe to leave in for every environment.
     */
    @Bean
    public ApplicationRunner ensureBucketExists(S3Client s3Client, @Value("${app.storage.bucket}") String bucket) {
        return (ApplicationArguments args) -> {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                log.info("Storage bucket '{}' does not exist yet - creating it.", bucket);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            }
        };
    }
}
