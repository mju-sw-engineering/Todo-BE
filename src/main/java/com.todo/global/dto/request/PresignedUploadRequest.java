package com.todo.global.dto.request;

import com.todo.global.dto.UploadType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        String contentType
) {}
