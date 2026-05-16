package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "투두 상세 응답")
public record TodoDetailResponse(

        @Schema(description = "투두 ID") Long todoId,
        @Schema(description = "제목") String title,
        @Schema(description = "마감 시간") LocalDateTime deadline,
        @Schema(description = "생성자 닉네임") String creatorNickname,
        @Schema(description = "공통 투두 상태") TodoStatus status,
        @Schema(description = "달성 인원 (성공 / 전체)", example = "2 / 5") String achievementCount,
        @Schema(description = "배정 팀원 인증 현황 목록") List<ParticipantDetailResponse> participants
) {}
