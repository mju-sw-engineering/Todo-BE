package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.AssignTodoWorkItemRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoActivePageResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemAssigneeResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemSubmissionResponse;
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

    @Operation(summary = "투두 생성", description = "assigneeIds를 전달하면 DIRECT Todo, tasks를 전달하면 TASK Todo를 생성합니다. 두 필드는 함께 전달할 수 없습니다.")
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
            summary = "마감 미경과(active) 투두 목록 조회",
            description = "날짜 지정 없이, 아직 마감이 지나지 않은 투두만 커서 기반으로 조회합니다. " +
                    "status=PENDING이면 진행 중(IN_PROGRESS)만, status=DONE이면 종료된 것(SUCCESS 또는 FAIL)만, " +
                    "생략하면 마감 안 지난 전체를 마감 임박 순으로 반환합니다. " +
                    "cursor 없으면 첫 페이지, 있으면 응답으로 받은 nextCursor를 그대로 다음 요청에 넣습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TodoActivePageResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "알 수 없는 status 값 또는 잘못된 cursor 형식",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoActivePageResponse>> getActiveTodoList(
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            @Parameter(description = "조회 상태: PENDING 또는 DONE (생략하면 전체)", example = "PENDING") String status,
            @Parameter(description = "다음 페이지 조회용 커서 (생략하면 첫 페이지)") String cursor,
            @Parameter(description = "조회 개수 (기본값: 5)") int size,
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

    @Operation(summary = "투두 상세 조회", description = "특정 투두의 상세 정보와 DIRECT 담당자 또는 TASK WorkItem 현황을 조회합니다.")
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
            summary = "인증 파일 제출",
            description = "DIRECT Todo의 로그인 담당자가 인증 파일(이미지 또는 문서)을 제출합니다. TASK는 /todo-work-items/{workItemId}/submission 경로를 사용합니다. " +
                    "마감 시간이 지난 경우 대상 WorkItem과 Todo를 FAIL로 변경 후 400을 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 파일 제출 성공"),
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
            summary = "인증 사진 이모지 반응 (deprecated)",
            description = "deprecated: Todo-FE와 제거 시점을 합의할 때까지 호환용으로 유지합니다. 새 연동은 /api/todo-work-items/{workItemId}/reactions를 사용합니다. 팀원의 인증 사진에 이모지 반응을 남깁니다. " +
                    "이미 같은 이모지를 눌렀으면 취소하고, 다른 이모지를 누르면 변경됩니다.",
            deprecated = true
    )
    @Deprecated(since = "2026-08-02", forRemoval = false)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이모지 반응 반영 성공",
                    content = @Content(schema = @Schema(implementation = TodoReactionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증 사진 미제출",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "WorkItem을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoReactionResponse>> reactTodoParticipant(
            @Parameter(description = "투두 참여자 ID", example = "1") Long participantId,
            ReactTodoRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "WorkItem 인증 사진 이모지 반응",
            description = "팀원의 WorkItem 인증 사진에 이모지 반응을 남깁니다. 이미 같은 이모지를 눌렀으면 취소하고, 다른 이모지를 누르면 변경됩니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이모지 반응 반영 성공",
                    content = @Content(schema = @Schema(implementation = TodoReactionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증 사진 미제출",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "WorkItem을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoReactionResponse>> reactTodoWorkItem(
            @Parameter(description = "WorkItem ID", example = "1") Long workItemId,
            ReactTodoRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "TASK WorkItem 인증 파일 제출",
            description = "로그인한 TASK 담당자가 WorkItem 인증 파일(이미지 또는 문서)을 제출합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 파일 제출 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "마감 초과 또는 유효하지 않은 파일 key",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "담당자가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "WorkItem을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 제출함",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<Void>> submitTodoWorkItem(
            @Parameter(description = "WorkItem ID", example = "1") Long workItemId,
            SubmitTodoRequest request,
            Authentication authentication
    );

    @Operation(summary = "WorkItem 제출 파일 조회", description = "팀 멤버에게 제출 파일의 원본 Presigned URL과 파일 종류·이름을 반환합니다. 썸네일 URL은 이미지 제출에만 존재합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TodoWorkItemSubmissionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "WorkItem이 없거나 미제출",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoWorkItemSubmissionResponse>> getTodoWorkItemSubmission(
            @Parameter(description = "WorkItem ID", example = "1") Long workItemId,
            Authentication authentication
    );

    @Operation(summary = "미배정 WorkItem 재배정", description = "팀 멤버가 진행 중이며 미배정인 DIRECT 또는 TASK WorkItem을 재배정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재배정 성공",
                    content = @Content(schema = @Schema(implementation = TodoWorkItemAssigneeResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미 배정됨, 새 담당자가 비팀원 또는 마감 경과",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 멤버가 아님",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "WorkItem을 찾을 수 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동시 재배정 충돌",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<ApiResponse<TodoWorkItemAssigneeResponse>> reassignTodoWorkItem(
            @Parameter(description = "WorkItem ID", example = "1") Long workItemId,
            AssignTodoWorkItemRequest request,
            Authentication authentication
    );
}
