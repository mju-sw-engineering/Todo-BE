package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
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

    @Operation(
            summary = "투두 리스트 조회",
            description = "팀의 투두 목록을 조회합니다. " +
                    "filter=IN_PROGRESS 또는 filter=ENDED로 상태 필터링하거나, " +
                    "date=yyyy-MM-dd(Asia/Seoul 기준)로 해당 날짜 마감 투두를 조회할 수 있습니다. " +
                    "filter와 date는 함께 사용할 수 없습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TodoSummaryResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "알 수 없는 필터, date 형식 오류 또는 filter·date 동시 사용",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> getTodoList(
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            @Parameter(description = "조회 필터: IN_PROGRESS 또는 ENDED (date와 동시 사용 불가)", example = "IN_PROGRESS") String filter,
            @Parameter(description = "조회 날짜 (Asia/Seoul 기준, yyyy-MM-dd, filter와 동시 사용 불가)", example = "2026-07-15") String date,
            Authentication authentication
    );

    @Operation(
            summary = "기간 투두 리포트 조회",
            description = "Asia/Seoul 기준 startDate부터 endDate까지의 팀 투두 통계와 액션 후보를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TodoPeriodReportResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "날짜 누락 또는 형식 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoPeriodReportResponse>> getTodoPeriodReport(
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            @Parameter(description = "조회 시작일", example = "2026-05-20") String startDate,
            @Parameter(description = "조회 종료일", example = "2026-05-26") String endDate,
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

    @Operation(
            summary = "인증 사진 제출",
            description = "배정된 팀원이 투두 완료 후 인증 사진을 제출하면 즉시 완료 처리됩니다. " +
                    "마감 시간이 지난 경우 상태를 FAIL로 변경 후 400을 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 사진 제출 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "마감 시간 초과",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 투두의 배정자가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "투두를 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<Void>> submitTodo(
            @Parameter(description = "투두 ID", example = "1") Long todoId,
            SubmitTodoRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "인증 사진 이모지 반응",
            description = "팀원의 인증 사진에 이모지 반응을 남깁니다. " +
                    "이미 같은 이모지를 눌렀으면 취소하고, 다른 이모지를 누르면 변경됩니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이모지 반응 반영 성공",
                    content = @Content(schema = @Schema(implementation = TodoReactionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증 사진 미제출",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "투두 참여자를 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoReactionResponse>> reactTodoParticipant(
            @Parameter(description = "투두 참여자 ID", example = "1") Long participantId,
            ReactTodoRequest request,
            Authentication authentication
    );
}
