package com.todo.global.dto.request;

import com.todo.global.dto.UploadType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "presigned PUT URL 발급 요청")
public record PresignedUploadRequest(
        @NotNull
        @Schema(description = "업로드 타입", example = "TEAM")
        UploadType type,

        @NotBlank
        @Schema(description = "원본 파일명 (확장자 추출용)", example = "image.png")
        String fileName,

        @NotBlank
        @Schema(description = "파일 MIME 타입", example = "image/png")
        String contentType,

        @Positive
        @Max(MAX_FILE_SIZE)
        @Schema(
                description = "업로드할 파일 크기(byte). 전달하면 해당 크기로 서명되어 다른 크기로는 업로드할 수 없습니다. "
                        + "미전달 시 크기 제한 없이 서명됩니다(구버전 클라이언트 호환).",
                example = "1048576"
        )
        Long fileSize
) {
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
}
