package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "Todo", description = "투두 API")
public interface TodoControllerDocs {

    @Operation(summary = "투두 생성", description = "팀 멤버가 팀 내 공통 투두를 생성하고 배정할 팀원을 지정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "투두 생성 성공",
                    content = @Content(schema = @Schema(implementation = CreateTodoResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<CreateTodoResponse>> createTodo(
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            CreateTodoRequest request,
            Authentication authentication
    );

    @Operation(summary = "투두 리스트 조회", description = "팀의 투두 목록을 조회합니다. 투두가 없으면 null과 안내 메시지를 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TodoSummaryResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> getTodoList(
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            Authentication authentication
    );

    @Operation(summary = "투두 상세 조회", description = "특정 투두의 상세 정보와 배정된 팀원들의 인증 현황을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TodoDetailResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "투두를 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoDetailResponse>> getTodoDetail(
            @Parameter(description = "투두 ID", example = "1") Long todoId,
            Authentication authentication
    );
}
