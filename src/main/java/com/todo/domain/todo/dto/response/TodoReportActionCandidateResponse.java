package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "투두 기간 리포트 액션 후보")
public record TodoReportActionCandidateResponse(

        @Schema(description = "후보 순위", example = "1")
        int rank,

        @Schema(description = "투두 ID", example = "10")
        Long todoId,

        @Schema(description = "제목")
        String title,

        @Schema(description = "마감 시간", example = "2026-05-26T21:00:00+09:00")
        OffsetDateTime deadline,

        @Schema(description = "공통 투두 상태")
        TodoStatus status,

        @Schema(description = "인증 완료 인원 수", example = "1")
        int achievementCount,

        @Schema(description = "참여 인원 수", example = "4")
        int participantCount,

        @Schema(description = "미인증 인원 수", example = "3")
        int unverifiedCount,

        @Schema(description = "진행률", example = "25")
        int progressRate
) {
    public static TodoReportActionCandidateResponse from(
            int rank,
            Long todoId,
            String title,
            OffsetDateTime deadline,
            TodoStatus status,
            int achievementCount,
            int participantCount,
            int unverifiedCount,
            int progressRate
    ) {
        return new TodoReportActionCandidateResponse(
                rank,
                todoId,
                title,
                deadline,
                status,
                achievementCount,
                participantCount,
                unverifiedCount,
                progressRate
        );
    }
}
