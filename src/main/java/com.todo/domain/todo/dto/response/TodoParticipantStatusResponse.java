package com.todo.domain.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "투두 참여자 인증 상태 응답")
public record TodoParticipantStatusResponse(

        @Schema(description = "멤버 ID")
        Long memberId,

        @Schema(description = "닉네임")
        String nickname,

        @Schema(description = "인증 상태", example = "미완료")
        String status
) {}
