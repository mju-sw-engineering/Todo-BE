package com.todo.domain.team.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "팀 초대 링크 발급 응답")
public record InviteLinkResponse(
        @Schema(description = "공유용 초대 링크", example = "https://todo.app/invite?token=XXXXX")
        String inviteLink,

        @Schema(description = "만료 시각 (7일 후)", example = "2026-08-18T21:00:00+09:00")
        OffsetDateTime expiresAt
) {
    /**
     * 팀 엔티티에는 토큰만 있고 링크 전체 문자열은 없으므로, 조합된 링크를 별도로 받아 응답을 만든다.
     * (ReauthResponse.of와 동일한 이유)
     */
    public static InviteLinkResponse of(String inviteLink, LocalDateTime expiresAt) {
        return new InviteLinkResponse(inviteLink, expiresAt.atOffset(ZoneOffset.ofHours(9)));
    }
}
