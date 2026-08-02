package com.todo.global.service;

import com.todo.global.config.MinioProperties;
import com.todo.global.dto.UploadType;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import com.todo.global.exception.FileStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final int THUMBNAIL_MAX_SIZE = 480;
    private static final double THUMBNAIL_QUALITY = 0.8;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;

    public PresignedUploadResponse generatePresignedPutUrl(Long userId, PresignedUploadRequest request) {
        validateImageContentType(request.contentType());

        String ext = extractExtension(request.fileName());
        String key = buildObjectKey(userId, request.type(), ext);

        PutObjectRequest.Builder putObjectRequest = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(request.contentType());
        // 크기를 함께 서명하면 해당 크기로만 업로드할 수 있어 대용량 업로드 남용을 막는다.
        if (request.fileSize() != null) {
            putObjectRequest.contentLength(request.fileSize());
        }

        String uploadUrl = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(props.getPutPresignedUrlExpiration()))
                        .putObjectRequest(putObjectRequest.build())
                        .build()
        ).url().toExternalForm();

        return new PresignedUploadResponse(uploadUrl, key);
    }

    public String createProofThumbnail(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        String thumbnailKey = buildThumbnailKey(objectKey);
        try {
            ResponseBytes<GetObjectResponse> source = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build());

            ByteArrayOutputStream thumbnailOutput = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(source.asByteArray()))
                    .size(THUMBNAIL_MAX_SIZE, THUMBNAIL_MAX_SIZE)
                    .outputFormat("jpg")
                    .outputQuality(THUMBNAIL_QUALITY)
                    .toOutputStream(thumbnailOutput);

            byte[] thumbnailBytes = thumbnailOutput.toByteArray();
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(props.getBucket())
                            .key(thumbnailKey)
                            .contentType("image/jpeg")
                            .contentLength((long) thumbnailBytes.length)
                            .build(),
                    RequestBody.fromBytes(thumbnailBytes));

            return thumbnailKey;
        } catch (Exception e) {
            log.warn("인증 사진 썸네일 생성 실패. objectKey={}", objectKey, e);
            return null;
        }
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
        try {
            deleteObjectOrThrow(objectKey);
        } catch (FileStorageException e) {
            log.warn("파일 삭제 실패. objectKey={}", objectKey, e);
        }
    }

    public void deleteObjectOrThrow(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build());
        } catch (SdkException e) {
            throw new FileStorageException("파일 삭제에 실패했습니다.", e);
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
            case PROOF -> "proofs/" + userId + "/" + uuid + suffix;
        };
    }

    private String buildThumbnailKey(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        String directory = slashIndex >= 0 ? objectKey.substring(0, slashIndex) : "";
        String fileName = slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String thumbnailFileName = baseName + ".jpg";
        return directory.isBlank()
                ? "thumbs/" + thumbnailFileName
                : directory + "/thumbs/" + thumbnailFileName;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private void validateImageContentType(String contentType) {
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("지원하지 않는 이미지 형식입니다.", HttpStatus.BAD_REQUEST);
        }
    }
}
