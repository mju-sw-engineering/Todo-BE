package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "투두 요약 응답")
public record TodoSummaryResponse(

        @Schema(description = "투두 ID")
        Long todoId,

        @Schema(description = "제목")
        String title,

        @Schema(description = "마감 시간", example = "2026-05-21T23:59:00+09:00")
        OffsetDateTime deadline,

        @Schema(description = "생성자 닉네임")
        String creatorNickname,

        @Schema(description = "공통 투두 상태")
        TodoStatus status,

        @Schema(description = "달성 인원 (성공 / 전체)", example = "2 / 5")
        String achievementCount,

        @Schema(description = "나의 상태 (완료 / 평가 대기중 / 미완료 / null)", example = "미완료")
        String myStatus,

        @Schema(description = "진행률", example = "40")
        int progressRate,

        @Schema(description = "참여자별 인증 상태 목록")
        List<TodoParticipantStatusResponse> participants
) {}
