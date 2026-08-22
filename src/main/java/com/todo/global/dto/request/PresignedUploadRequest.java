package com.todo.global.dto.request;

import com.todo.global.dto.UploadType;
import io.swagger.v3.oas.annotations.media.Schema;
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

        @NotNull
        @Positive
        @Schema(
                description = "업로드할 파일 크기(byte). 해당 크기로 서명되어 다른 크기로는 업로드할 수 없습니다. "
                        + "업로드 타입·형식별 상한은 서버가 별도로 검증합니다.",
                example = "1048576"
        )
        Long fileSize,

        @Schema(
                description = "회원가입 진행 중 비로그인 PROFILE 업로드에만 쓰는 토큰. "
                        + "이메일 가입은 emailVerificationToken, 애플 가입은 setupToken을 그대로 전달합니다. "
                        + "전달하면 발급 한도를 IP가 아닌 가입자 단위로 적용해, 같은 공용 IP(학교 등)를 쓰는 "
                        + "다른 사용자와 한도를 나눠 쓰지 않습니다. 미전달 시 IP 기준으로 제한됩니다."
        )
        String signupToken,

        @Schema(description = "PROOF 타입 업로드에만 필수. 인증 파일이 속할 투두 ID", example = "1")
        Long todoId
) {
}
