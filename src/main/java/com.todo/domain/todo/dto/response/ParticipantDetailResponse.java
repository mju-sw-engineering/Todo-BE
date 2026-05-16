package com.todo.domain.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "배정 팀원 인증 현황")
public record ParticipantDetailResponse(

        @Schema(description = "유저 ID") Long userId,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "인증 사진 URL") String proofImageUrl,
        @Schema(description = "인증 상태 (완료 / 평가 대기중 / 미완료)", example = "미완료") String status
) {}
