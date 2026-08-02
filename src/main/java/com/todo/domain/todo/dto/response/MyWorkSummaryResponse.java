package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.Todo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "한 Todo 안에서 로그인 사용자가 맡은 WorkItem 상태 요약")
public record MyWorkSummaryResponse(

        @Schema(description = "전체 담당 WorkItem 수", example = "2") int totalCount,
        @Schema(description = "성공 WorkItem 수", example = "1") int successCount,
        @Schema(description = "실패 WorkItem 수", example = "0") int failCount,
        @Schema(description = "진행 중 WorkItem 수", example = "1") int inProgressCount
) {
    public static MyWorkSummaryResponse from(
            Todo todo,
            int totalCount,
            int successCount,
            int failCount,
            int inProgressCount
    ) {
        return new MyWorkSummaryResponse(totalCount, successCount, failCount, inProgressCount);
    }
}
