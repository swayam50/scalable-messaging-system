package io.wulfcodes.messaging.media.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.wulfcodes.messaging.media.config.MediaProperties;
import io.wulfcodes.messaging.media.config.S3ClientConfig;
import io.wulfcodes.messaging.media.exception.StorageUnavailableException;
import io.wulfcodes.messaging.media.service.spec.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Object storage over the S3 API (SeaweedFS locally; AWS S3 / R2 / any S3-compatible store in
 * production), using the MinIO Java SDK purely as an S3 client. Browsers upload/download directly
 * against the store with presigned requests, so file bytes never pass through this JVM.
 */
@Slf4j
@Service
public class S3StorageService implements StorageService {

    private final MinioClient storageClient;
    private final MinioClient presignClient;
    private final MediaProperties.Storage storage;

    public S3StorageService(@Qualifier(S3ClientConfig.STORAGE_CLIENT) MinioClient storageClient,
                            @Qualifier(S3ClientConfig.PRESIGN_CLIENT) MinioClient presignClient,
                            MediaProperties properties) {
        this.storageClient = storageClient;
        this.presignClient = presignClient;
        this.storage = properties.storage();
    }

    /** Creates the bucket on startup if needed (retries while MinIO is still booting). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                if (!storageClient.bucketExists(BucketExistsArgs.builder().bucket(storage.bucket()).build())) {
                    storageClient.makeBucket(MakeBucketArgs.builder().bucket(storage.bucket()).build());
                    log.info("Created bucket {}", storage.bucket());
                }
                return;
            } catch (Exception e) {
                log.info("Waiting for object storage ({}): {}", attempt, e.getMessage());
                sleep();
            }
        }
        log.error("Object storage not reachable; uploads will fail until it is");
    }

    /**
     * A POST policy (rather than a presigned PUT) lets the storage itself enforce the rules:
     * exact key, exact Content-Type, and size between 1 byte and maxBytes. A client cannot upload
     * a 2 GB file just because it was given an upload URL.
     */
    @Override
    public Map<String, String> presignUpload(String objectKey, String mimeType, long maxBytes, Instant expiresAt) {
        PostPolicy policy = new PostPolicy(storage.bucket(), expiresAt.atZone(ZoneOffset.UTC));
        policy.addEqualsCondition("key", objectKey);
        policy.addEqualsCondition("Content-Type", mimeType);
        policy.addContentLengthRangeCondition(1L, maxBytes);
        try {
            Map<String, String> fields = new HashMap<>(presignClient.getPresignedPostFormData(policy));
            fields.put("key", objectKey);
            fields.put("Content-Type", mimeType);
            return fields;
        } catch (MinioException e) {
            throw new StorageUnavailableException("Could not create upload policy");
        }
    }

    @Override
    public String uploadUrl() {
        return storage.publicEndpoint().replaceAll("/$", "") + "/" + storage.bucket();
    }

    @Override
    public Optional<StoredObject> stat(String objectKey) {
        try {
            StatObjectResponse stat = storageClient.statObject(
                    StatObjectArgs.builder().bucket(storage.bucket()).object(objectKey).build());
            return Optional.of(new StoredObject(stat.size(), stat.contentType()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchObject".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new StorageUnavailableException("Could not check uploaded object");
        } catch (MinioException e) {
            throw new StorageUnavailableException("Could not check uploaded object");
        }
    }

    @Override
    public String presignDownload(String objectKey, String fileName, String mimeType, boolean asDownload, int expirySeconds) {
        // Override response headers so the browser renders media inline but saves plain files.
        String disposition = (asDownload ? "attachment" : "inline")
                + "; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(storage.bucket())
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .extraQueryParams(Map.of(
                            "response-content-disposition", disposition,
                            "response-content-type", mimeType))
                    .build());
        } catch (MinioException e) {
            throw new StorageUnavailableException("Could not create download link");
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
