package com.todo.global.service;

import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.repository.TodoRepository;
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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
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
    private static final long MAX_PROOF_IMAGE_SIZE = 5L * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;
    private final TodoRepository todoRepository;
    private final TeamMemberRepository teamMemberRepository;

    public PresignedUploadResponse generatePresignedPutUrl(Long userId, PresignedUploadRequest request) {
        validateImageContentType(request.contentType());

        String ext = extractExtension(request.fileName());
        String key = request.type() == UploadType.PROOF
                ? buildProofObjectKey(userId, request.todoId(), ext)
                : buildObjectKey(userId, request.type(), ext);

        // 크기 없이 서명하면 URL 하나로 무제한 크기를 업로드할 수 있다. DTO 검증(@NotNull)이
        // 막지만, 다른 호출 경로가 생겨도 무제한 서명이 조용히 발급되지 않도록 여기서도 막는다.
        if (request.fileSize() == null) {
            throw new BusinessException("파일 크기는 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        // 크기를 함께 서명하면 해당 크기로만 업로드할 수 있어 대용량 업로드 남용을 막는다.
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(request.contentType())
                .contentLength(request.fileSize())
                .build();

        String uploadUrl = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(props.getPutPresignedUrlExpiration()))
                        .putObjectRequest(putObjectRequest)
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

    /**
     * 프로필 이미지 object key의 소유권을 검증한다. 저장 직전마다 호출해야 한다.
     *
     * <p>키는 {@link #buildObjectKey}가 만든 경로만 유효하다 — 로그인 상태 발급은
     * {@code profiles/{userId}/}, 회원가입용 비로그인 발급은 {@code profiles/temp/}.
     * 검증 없이 저장하면 남의 object key(예: 다른 사용자의 인증 사진)를 프로필로 등록해
     * presigned GET으로 열람하거나, 이전-이미지 삭제 예약 경로로 남의 파일을 지울 수 있다.
     *
     * @param userId 비로그인 요청(회원가입)이면 null — temp 경로만 허용된다
     */
    public void validateProfileImageKey(Long userId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        boolean owned = objectKey.startsWith("profiles/temp/")
                || (userId != null && objectKey.startsWith("profiles/" + userId + "/"));
        if (!owned) {
            throw new BusinessException("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    /** 팀 이미지 object key의 소유권 검증. {@link #validateProfileImageKey}와 같은 이유로 저장 직전에 호출한다. */
    public void validateTeamImageKey(Long userId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        if (!objectKey.startsWith("teams/temp/" + userId + "/")) {
            throw new BusinessException("본인이 업로드한 팀 이미지만 사용할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Presigned URL로 업로드된 인증 사진을 제출 직전에 다시 검증한다.
     * 서명 시 크기를 강제하지만, 실제 저장된 객체의 크기와 MIME은 HEAD 결과를 기준으로
     * 한 번 더 확인한다 (서명 정책 변경·수동 업로드 등에 대한 방어).
     *
     * <p>썸네일 여부는 DB 조회 없이 바로 판별되므로, teamId 조회보다 먼저 확인해 불필요한
     * 조회를 피한다.
     */
    public void validateProofImage(Long userId, Long todoId, String objectKey) {
        if (objectKey != null && objectKey.contains("/thumbs/")) {
            throw new BusinessException("썸네일 파일은 인증 사진으로 제출할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        Long teamId = resolveTodoTeamId(todoId);
        String expectedPrefix = "proofs/" + teamId + "/" + todoId + "/" + userId + "/";
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(expectedPrefix)) {
            throw new BusinessException("본인이 업로드한 인증 사진만 제출할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        HeadObjectResponse object;
        try {
            object = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException("인증 사진 파일을 찾을 수 없습니다.", HttpStatus.BAD_REQUEST);
            }
            throw new FileStorageException("인증 사진 파일을 확인하는 데 실패했습니다.", e);
        } catch (SdkException e) {
            throw new FileStorageException("인증 사진 파일을 확인하는 데 실패했습니다.", e);
        }

        validateImageContentType(object.contentType());
        if (object.contentLength() != null && object.contentLength() > MAX_PROOF_IMAGE_SIZE) {
            throw new BusinessException("인증 사진은 5MB 이하만 제출할 수 있습니다.", HttpStatus.BAD_REQUEST);
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

    public Duration getPresignedUrlExpiration() {
        return Duration.ofSeconds(props.getPresignedUrlExpiration());
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
            case PROOF -> throw new IllegalStateException("PROOF는 buildProofObjectKey로 처리됩니다.");
        };
    }

    /**
     * 인증 파일은 팀·투두 단위로 확인하기 쉽도록 {@code proofs/{teamId}/{todoId}/{userId}/}
     * 아래에 저장한다. teamId는 클라이언트가 보내지 않고 todoId로 서버가 직접 조회한다 —
     * 클라이언트가 teamId를 조작해서 보낼 여지를 없애고, 이 조회가 팀 멤버 검증도 겸한다.
     */
    private String buildProofObjectKey(Long userId, Long todoId, String ext) {
        if (todoId == null) {
            throw new BusinessException("인증 파일 업로드에는 투두 ID가 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        Long teamId = resolveTodoTeamId(todoId);
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new BusinessException("해당 투두가 속한 팀의 멤버만 인증 파일을 업로드할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        String suffix = ext.isEmpty() ? "" : "." + ext;
        String uuid = UUID.randomUUID().toString();
        return "proofs/" + teamId + "/" + todoId + "/" + userId + "/" + uuid + suffix;
    }

    private Long resolveTodoTeamId(Long todoId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.BAD_REQUEST));
        return todo.getTeam().getId();
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
