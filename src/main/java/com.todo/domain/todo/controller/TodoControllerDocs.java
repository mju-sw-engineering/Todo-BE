package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Todo", description = "투두 API")
public interface TodoControllerDocs {

    @Operation(
            summary = "투두 생성",
            description = "팀 멤버가 팀 내 공통 투두를 생성하고 배정할 팀원을 지정합니다. " +
                    "요청자가 해당 팀의 멤버가 아닐 경우 403을 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "투두 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<CreateTodoResponse>> createTodo(
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            CreateTodoRequest request,
            Authentication authentication
    );
}
