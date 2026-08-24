package com.todo.domain.todo.recommendation.controller;

import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "팀 할 일 추천", description = "`/할일추천` 결과 카드에서 할 일을 등록합니다.")
public interface TeamTodoRecommendationControllerDocs {

    @Operation(
            summary = "추천 카드에서 할 일 등록",
            description = """
                    추천 카드의 한 항목을 그대로 팀 할 일로 등록합니다. 카드 데이터는 명령어 실행 결과
                    조회(`GET /api/teams/{teamId}/chat/messages/{messageId}/command-result`)로 받은
                    `result.items[index]`이며, 등록되면 그 결과의 해당 항목에 `registeredTodoId`가 채워집니다.
                    담당자는 추천의 `suggestedAssigneeIds`를 쓰고, 비어 있으면 요청자 본인이 됩니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀원이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "추천 결과 또는 항목 없음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 추천")
    ResponseEntity<ApiResponse<CreateTodoResponse>> register(
            @Parameter(description = "팀 ID") Long teamId,
            @Parameter(description = "명령어를 촉발한 채팅 메시지 ID") Long messageId,
            @Parameter(description = "추천 항목 index (0부터)") int index,
            Authentication authentication
    );
}
