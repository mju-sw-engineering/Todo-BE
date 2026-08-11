package com.todo.domain.auth.dto.response;

import com.todo.domain.auth.entity.RefreshToken;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "활성 세션(로그인된 기기) 정보")
public record SessionResponse(
        @Schema(description = "세션 ID (개별 로그아웃에 사용)")
        Long id,

        @Schema(description = "클라이언트가 보낸 기기 고유 ID. 클라이언트가 보내지 않았다면 null", nullable = true)
        String deviceId,

        @Schema(description = "로그인 시각", example = "2026-08-11T21:00:00+09:00")
        OffsetDateTime createdAt,

        @Schema(description = "만료 시각", example = "2026-08-25T21:00:00+09:00")
        OffsetDateTime expiresAt,

        @Schema(description = "이 요청을 보낸 기기의 세션인지 여부")
        boolean current
) {
    public static SessionResponse from(RefreshToken token, boolean current) {
        return new SessionResponse(
                token.getId(),
                token.getDeviceId(),
                token.getCreatedAt().atOffset(ZoneOffset.ofHours(9)),
                token.getExpiresAt().atOffset(ZoneOffset.ofHours(9)),
                current
        );
    }
}
