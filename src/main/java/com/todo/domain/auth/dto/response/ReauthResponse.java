package com.todo.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "재인증 응답")
public record ReauthResponse(

        @Schema(description = "재인증 토큰. 응답으로 한 번만 내려가며 서버는 해시만 보관한다")
        String reauthToken,

        @Schema(description = "만료 시각", example = "2026-08-01T12:05:00+09:00")
        OffsetDateTime expiresAt
) {
    /**
     * 엔티티에는 해시만 있으므로 원문 토큰을 별도로 받아 응답을 만든다.
     */
    public static ReauthResponse of(String rawToken, java.time.LocalDateTime expiresAt) {
        return new ReauthResponse(rawToken, expiresAt.atOffset(ZoneOffset.ofHours(9)));
    }
}
