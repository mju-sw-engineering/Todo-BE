package com.todo.global.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "presigned PUT URL 발급 응답")
public record PresignedUploadResponse(
        @Schema(description = "MinIO에 직접 PUT 업로드할 URL (10분 유효)")
        String uploadUrl,

        @Schema(description = "업로드 완료 후 DB 저장에 사용할 object key")
        String objectKey
) {}
