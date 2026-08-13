package com.todo.domain.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "마감 미경과 투두 커서 페이지 응답")
public record TodoActivePageResponse(

        @Schema(description = "투두 목록") List<TodoSummaryResponse> todos,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext,
        @Schema(description = "다음 페이지 조회용 커서. 내부 구조를 해석하지 말고 그대로 다음 요청의 cursor에 사용") String nextCursor
) {}
