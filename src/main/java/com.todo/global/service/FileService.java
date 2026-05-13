package com.todo.global.service;

import com.todo.global.config.MinioProperties;
import com.todo.global.dto.UploadType;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;

    @PostConstruct
    public void initBucket() {
        try {
            ensureBucketExists(props.getBucket());
        } catch (SdkClientException | S3Exception e) {
            // TODO: MinIO local dependency is temporarily optional. Remove this bypass once MinIO is required on boot again.
            log.warn("MinIO bucket 초기화를 건너뜁니다. message: {}", e.getMessage());
        }
    }

    public PresignedUploadResponse generatePresignedPutUrl(Long userId, PresignedUploadRequest request) {
        String ext = extractExtension(request.fileName());
        String key = buildObjectKey(userId, request.type(), ext);

        String uploadUrl = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(props.getPutPresignedUrlExpiration()))
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(props.getBucket())
                                .key(key)
                                .contentType(request.contentType())
                                .build())
                        .build()
        ).url().toExternalForm();

        return new PresignedUploadResponse(uploadUrl, key);
    }

    public String resolveImageUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(props.getPresignedUrlExpiration()))
                        .getObjectRequest(r -> r.bucket(props.getBucket()).key(objectKey))
                        .build()
        ).url().toExternalForm();
    }

    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            log.warn("MinIO object 삭제 실패 — key: {}, message: {}", objectKey, e.getMessage());
        }
    }

    private String buildObjectKey(Long userId, UploadType type, String ext) {
        String suffix = ext.isEmpty() ? "" : "." + ext;
        String uuid = UUID.randomUUID().toString();
        return switch (type) {
            case TEAM -> "teams/temp/" + userId + "/" + uuid + suffix;
            case PROFILE -> userId != null
                    ? "profiles/" + userId + "/" + uuid + suffix
                    : "profiles/temp/" + uuid + suffix;
        };
    }

    private void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
