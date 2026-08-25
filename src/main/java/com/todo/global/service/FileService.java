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
import com.todo.global.file.entity.UploadLedger;
import com.todo.global.file.repository.UploadLedgerRepository;
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
import java.nio.charset.StandardCharsets;
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
    private static final String HWP_CONTENT_TYPE = "application/x-hwp";
    private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";
    private static final String HWP_EXTENSION = "hwp";
    /** hwpx가 ZIP 안에 담아두는 규격상의 MIME. 일부 클라이언트가 이 값을 그대로 보낸다. */
    private static final String HWPX_ZIP_CONTENT_TYPE = "application/hwp+zip";
    private static final String HWPX_CONTENT_TYPE = "application/vnd.hancom.hwpx";
    private static final String HWPX_EXTENSION = "hwpx";
    private static final Set<String> ALLOWED_PROOF_DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            HWP_CONTENT_TYPE,
            HWPX_CONTENT_TYPE,
            HWPX_ZIP_CONTENT_TYPE
    );
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final String HWP_MARKER = "HWP Document File";
    private static final int HWP_MARKER_SCAN_RANGE = 256 * 1024;
    private static final int MAX_EXTENSION_LENGTH = 10;
    private static final int THUMBNAIL_MAX_SIZE = 480;
    private static final double THUMBNAIL_QUALITY = 0.8;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PROOF_DOCUMENT_SIZE = 20L * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;
    private final TodoRepository todoRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UploadLedgerRepository uploadLedgerRepository;

    public PresignedUploadResponse generatePresignedPutUrl(Long userId, PresignedUploadRequest request) {
        String ext = extractExtension(request.fileName());
        validateContentType(request.type(), request.contentType(), ext);

        String key = request.type() == UploadType.PROOF
                ? buildProofObjectKey(userId, request.todoId(), ext)
                : buildObjectKey(userId, request.type(), ext);

        // 크기 없이 서명하면 URL 하나로 무제한 크기를 업로드할 수 있다. DTO 검증(@NotNull)이
        // 막지만, 다른 호출 경로가 생겨도 무제한 서명이 조용히 발급되지 않도록 여기서도 막는다.
        if (request.fileSize() == null) {
            throw new BusinessException("파일 크기는 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        validateFileSize(request.type(), request.contentType(), ext, request.fileSize());

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

        // 발급 원장. 업로드만 되고 제출되지 않은 파일을 고아 정리 스케줄러가 찾는 근거다.
        // 발급됐지만 실제 업로드가 없던 키도 행으로 남는데, 그 삭제 시도는 S3에서 no-op이다.
        uploadLedgerRepository.save(UploadLedger.create(key));

        return new PresignedUploadResponse(uploadUrl, key);
    }

    /**
     * 이미지 여부는 확장자가 아니라 contentType으로 판단한다. 확장자는 클라이언트가 붙인
     * 파일명에서 온 것이라 실제 내용과 다를 수 있고(예: PDF를 a.jpg로 업로드),
     * contentType은 presigned PUT 서명에 포함돼 업로드 시점에 강제된 값이다.
     */
    public String createProofThumbnail(String objectKey, String contentType) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
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
     * Presigned URL로 업로드된 인증 파일을 제출 직전에 다시 검증한다.
     * 서명 시 크기를 강제하지만, 실제 저장된 객체의 크기와 MIME은 HEAD 결과를 기준으로
     * 한 번 더 확인한다 (서명 정책 변경·수동 업로드 등에 대한 방어). 문서 형식(PDF/OOXML/HWP)은
     * 여기서 실제 바이트 시그니처까지 확인해, 확장자만 바꿔 올린 파일이 나중에 만들 미리보기
     * 기능을 오작동시키지 않도록 한다.
     *
     * <p>썸네일 여부는 DB 조회 없이 바로 판별되므로, teamId 조회보다 먼저 확인해 불필요한
     * 조회를 피한다.
     *
     * @return HEAD 결과로 확정된 contentType. 이후 이미지/문서 분기(썸네일 생성 등)는
     *         확장자 추측이 아니라 이 값을 기준으로 한다.
     */
    public String validateProofFile(Long userId, Long todoId, String objectKey) {
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

        String ext = extractExtension(objectKey);
        validateContentType(UploadType.PROOF, object.contentType(), ext);
        if (object.contentLength() != null) {
            validateFileSize(UploadType.PROOF, object.contentType(), ext, object.contentLength());
        }
        validateFileSignature(objectKey, object.contentType(), ext);
        return object.contentType();
    }

    /**
     * 저장된 객체를 통째로 읽는다. AI 판정처럼 서버가 파일 내용을 직접 다뤄야 할 때 쓴다.
     * presigned URL을 외부 서비스에 넘기면 그 URL이 어디까지 흘러가는지 통제할 수 없어,
     * 서버가 바이트를 받아 필요한 형태로 변환해 전달한다.
     *
     * <p>PROOF 업로드는 이미지 5MB·문서 20MB로 제한돼 있어 통째로 메모리에 올려도 안전하다.
     */
    public byte[] readObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException("파일 키가 비어 있습니다.", HttpStatus.BAD_REQUEST);
        }
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (SdkException e) {
            throw new FileStorageException("파일을 읽는 데 실패했습니다.", e);
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

    /**
     * 확장자는 클라이언트가 보낸 파일명에서 오고 오브젝트 키의 suffix로 그대로 붙는다.
     * 영숫자 토큰이 아니면 확장자가 없는 것으로 취급한다 — {@code a.jpg/thumbs/x} 같은
     * 파일명이 키에 슬래시를 끼워 넣어 이상한 경로를 만드는 걸 막는다.
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        if (ext.isEmpty() || ext.length() > MAX_EXTENSION_LENGTH || !ext.chars().allMatch(Character::isLetterOrDigit)) {
            return "";
        }
        return ext;
    }

    /**
     * PROFILE/TEAM은 이미지만 허용한다. PROOF는 이미지에 더해 문서(PDF/docx/xlsx/csv/HWP)도
     * 허용하는데, HWP는 IANA 등록 MIME이 없어 브라우저가 {@code application/octet-stream}을
     * 보낼 수 있다 — 그래서 PROOF에 한해 확장자가 {@code .hwp}일 때만 그 값을 예외적으로
     * 허용한다(다른 확장자에 octet-stream을 붙여 보내는 건 여전히 거절).
     */
    private void validateContentType(UploadType type, String contentType, String ext) {
        if (type != UploadType.PROOF) {
            if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
                throw new BusinessException(
                        "지원하지 않는 이미지 형식입니다. JPG·PNG·WebP만 업로드할 수 있습니다.",
                        HttpStatus.BAD_REQUEST);
            }
            return;
        }

        boolean allowed = ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType) || isProofDocument(contentType, ext);
        if (!allowed) {
            // 어떤 형식이 되는지까지 알려줘야 사용자가 대처할 수 있다. 목록에 없는 형식(구형
            // .doc/.xls 등)을 올린 사용자가 무엇으로 바꿔 저장해야 하는지 바로 알 수 있다.
            throw new BusinessException(
                    "지원하지 않는 파일 형식입니다. "
                            + "이미지(JPG·PNG·WebP)와 문서(PDF·DOCX·XLSX·CSV·HWP·HWPX)만 업로드할 수 있습니다.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** 문서(PDF/docx/xlsx/csv/HWP)는 20MB, 그 외 이미지는 5MB가 상한이다. */
    private void validateFileSize(UploadType type, String contentType, String ext, long fileSize) {
        boolean isDocument = type == UploadType.PROOF && isProofDocument(contentType, ext);
        if (isDocument) {
            if (fileSize > MAX_PROOF_DOCUMENT_SIZE) {
                throw new BusinessException("인증 파일은 20MB 이하만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (fileSize > MAX_IMAGE_SIZE) {
            throw new BusinessException("이미지는 5MB 이하만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * PROOF 요청의 contentType·확장자가 문서(PDF/docx/xlsx/csv/HWP) 카테고리에 해당하는지
     * 판단한다. {@link #validateContentType}과 {@link #validateFileSize}가 같은 기준으로
     * 문서 여부를 판단해야 해서(특히 HWP의 octet-stream 특례) 하나로 모아둔다.
     */
    private boolean isProofDocument(String contentType, String ext) {
        return ALLOWED_PROOF_DOCUMENT_CONTENT_TYPES.contains(contentType)
                || isHwpUpload(contentType, ext)
                || isHwpxUpload(contentType, ext);
    }

    /** HWP 판정도 contentType이 우선이고, octet-stream은 {@code .hwp} 확장자 조합만 인정한다. */
    private boolean isHwpUpload(String contentType, String ext) {
        return HWP_CONTENT_TYPE.equals(contentType)
                || (OCTET_STREAM_CONTENT_TYPE.equals(contentType) && HWP_EXTENSION.equalsIgnoreCase(ext));
    }

    /**
     * hwpx는 hwp와 이름만 비슷할 뿐 실제로는 ZIP + XML(OWPML) 컨테이너라, 시그니처 검증도
     * OLE2가 아니라 docx/xlsx와 같은 ZIP 경로를 탄다. 등록된 MIME이 없어 브라우저가
     * octet-stream을 보내는 것도 hwp와 같아서 확장자 특례를 동일하게 둔다.
     */
    private boolean isHwpxUpload(String contentType, String ext) {
        return HWPX_CONTENT_TYPE.equals(contentType)
                || HWPX_ZIP_CONTENT_TYPE.equals(contentType)
                || (OCTET_STREAM_CONTENT_TYPE.equals(contentType) && HWPX_EXTENSION.equalsIgnoreCase(ext));
    }

    /**
     * 나중에 만들 미리보기 기능이 확장자만 바꿔치기된 파일 때문에 오작동하지 않도록, 가벼운
     * 매직바이트 검증을 한다. docx/xlsx를 서로 구분하는 zip 내부 검사는 하지 않고, CSV는
     * 신뢰할 시그니처가 없어 검증을 건너뛴다.
     *
     * <p>모든 분기는 contentType 기준이다. 확장자를 기준으로 삼으면 {@code application/x-hwp}에
     * 다른 확장자를 붙이는 식으로 어떤 시그니처 검증에도 걸리지 않는 조합이 생긴다. 확장자는
     * HWP의 octet-stream 특례(발급 단계에서 {@code .hwp}로 제한됨)를 다시 확인할 때만 쓴다.
     */
    private void validateFileSignature(String objectKey, String contentType, String ext) {
        if ("application/pdf".equals(contentType)) {
            if (!startsWith(readObjectPrefix(objectKey, PDF_MAGIC.length), PDF_MAGIC)) {
                throw new BusinessException("파일 내용이 형식과 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (isOoxmlContentType(contentType) || isHwpxUpload(contentType, ext)) {
            if (!startsWith(readObjectPrefix(objectKey, ZIP_MAGIC.length), ZIP_MAGIC)) {
                throw new BusinessException("파일 내용이 형식과 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (isHwpUpload(contentType, ext)) {
            byte[] window = readObjectPrefix(objectKey, HWP_MARKER_SCAN_RANGE);
            boolean valid = startsWith(window, OLE2_MAGIC)
                    && new String(window, StandardCharsets.US_ASCII).contains(HWP_MARKER);
            if (!valid) {
                throw new BusinessException("파일 내용이 형식과 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private boolean isOoxmlContentType(String contentType) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType);
    }

    private byte[] readObjectPrefix(String objectKey, int maxBytes) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .range("bytes=0-" + (maxBytes - 1))
                    .build()).asByteArray();
        } catch (SdkException e) {
            throw new FileStorageException("파일 내용을 확인하는 데 실패했습니다.", e);
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
