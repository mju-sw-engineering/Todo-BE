package com.todo.domain.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "투두 인증 사진 제출 요청")
public record SubmitTodoRequest(

        @NotBlank
        @Schema(description = "인증 사진 오브젝트 키 (presigned-upload 후 반환된 값)", example = "proofs/1/uuid.jpg")
        String proofImageKey
) {}
