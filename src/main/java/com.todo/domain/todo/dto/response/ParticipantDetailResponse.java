package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoReactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "배정 팀원 인증 현황")
public record ParticipantDetailResponse(

        @Schema(description = "투두 참여자 ID") Long todoParticipantId,
        @Schema(description = "유저 ID") Long userId,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "인증 사진 URL") String proofImageUrl,
        @Schema(description = "인증 상태 (완료 / 미완료)", example = "미완료") String status,
        @Schema(description = "이모지 반응 집계") List<TodoReactionResponse> reactions,
        @Schema(description = "내가 누른 이모지 반응", example = "LIKE") TodoReactionType myReaction
) {}
