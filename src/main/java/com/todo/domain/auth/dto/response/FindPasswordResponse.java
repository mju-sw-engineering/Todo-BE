package com.todo.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "비밀번호 재설정 토큰 발급 응답")
public record FindPasswordResponse(
        @Schema(description = "비밀번호 재설정 토큰 (PATCH /api/auth/password/reset에 사용, 15분 이내)")
        String passwordResetToken
) {
    /**
     * 엔티티에는 해시만 있으므로 원문 토큰을 별도로 받아 응답을 만든다. (ReauthResponse.of와 동일한 이유)
     */
    public static FindPasswordResponse of(String rawToken) {
        return new FindPasswordResponse(rawToken);
    }
}
