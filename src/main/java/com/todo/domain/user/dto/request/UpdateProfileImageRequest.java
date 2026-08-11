package com.todo.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileImageRequest(
        @NotBlank(message = "프로필 이미지 키는 필수입니다")
        @Schema(description = "presigned-upload로 업로드한 프로필 이미지의 object key", example = "profiles/1/uuid.png")
        String profileImageKey
) {
}
