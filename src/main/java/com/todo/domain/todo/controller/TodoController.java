package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.AssignTodoWorkItemRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemAssigneeResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemSubmissionResponse;
import com.todo.domain.todo.service.TodoService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TodoController implements TodoControllerDocs {

    private final TodoService todoService;

    @PostMapping("/teams/{teamId}/todos")
    public ResponseEntity<ApiResponse<CreateTodoResponse>> createTodo(
            @PathVariable Long teamId,
            @Valid @RequestBody CreateTodoRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        CreateTodoResponse response = todoService.createTodo(userId, teamId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "투두가 생성되었습니다."));
    }

    @GetMapping("/teams/{teamId}/todos")
    public ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> getTodoList(
            @PathVariable Long teamId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String date,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<TodoSummaryResponse> result = todoService.getTodoList(teamId, userId, filter, date);
        if (result.isEmpty()) {
            String message = (date != null && !date.isBlank())
                    ? "해당 날짜의 할 일이 없습니다"
                    : "조회된 할 일이 없습니다";
            return ResponseEntity.ok(ApiResponse.success(null, message));
        }
        return ResponseEntity.ok(ApiResponse.success(result, "할 일 목록을 조회했습니다"));
    }

    @GetMapping("/teams/{teamId}/todos/report")
    public ResponseEntity<ApiResponse<TodoPeriodReportResponse>> getTodoPeriodReport(
            @PathVariable Long teamId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        TodoPeriodReportResponse response = todoService.getTodoPeriodReport(
                teamId,
                userId,
                startDate,
                endDate
        );
        return ResponseEntity.ok(ApiResponse.success(response, "기간별 리포트를 조회했습니다"));
    }

    @GetMapping("/todos/{todoId}")
    public ResponseEntity<ApiResponse<TodoDetailResponse>> getTodoDetail(
            @PathVariable Long todoId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        TodoDetailResponse response = todoService.getTodoDetail(todoId, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "할 일 상세를 조회했습니다"));
    }

    @PostMapping("/todo-participants/{participantId}/reactions")
    @Deprecated(since = "2026-08-02", forRemoval = false)
    public ResponseEntity<ApiResponse<TodoReactionResponse>> reactTodoParticipant(
            @PathVariable Long participantId,
            @Valid @RequestBody ReactTodoRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        TodoReactionResponse response = todoService.reactTodoParticipant(participantId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "이모지 반응이 반영되었습니다."));
    }

    @PostMapping("/todo-work-items/{workItemId}/reactions")
    public ResponseEntity<ApiResponse<TodoReactionResponse>> reactTodoWorkItem(
            @PathVariable Long workItemId,
            @Valid @RequestBody ReactTodoRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        TodoReactionResponse response = todoService.reactTodoWorkItem(workItemId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "이모지 반응이 반영되었습니다."));
    }

    @PostMapping("/todos/{todoId}/submit")
    public ResponseEntity<ApiResponse<Void>> submitTodo(
            @PathVariable Long todoId,
            @Valid @RequestBody SubmitTodoRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        todoService.submitTodo(todoId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "인증 사진이 제출되었습니다."));
    }

    @PostMapping("/todo-work-items/{workItemId}/submission")
    public ResponseEntity<ApiResponse<Void>> submitTodoWorkItem(
            @PathVariable Long workItemId,
            @Valid @RequestBody SubmitTodoRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        todoService.submitTodoWorkItem(workItemId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "인증 사진이 제출되었습니다."));
    }

    @GetMapping("/todo-work-items/{workItemId}/submission")
    public ResponseEntity<ApiResponse<TodoWorkItemSubmissionResponse>> getTodoWorkItemSubmission(
            @PathVariable Long workItemId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        TodoWorkItemSubmissionResponse response = todoService.getTodoWorkItemSubmission(workItemId, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "제출 내역을 조회했습니다"));
    }

    @PatchMapping("/todo-work-items/{workItemId}/assignee")
    public ResponseEntity<ApiResponse<TodoWorkItemAssigneeResponse>> reassignTodoWorkItem(
            @PathVariable Long workItemId,
            @Valid @RequestBody AssignTodoWorkItemRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        TodoWorkItemAssigneeResponse response = todoService.reassignTodoWorkItem(workItemId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "담당자가 재배정되었습니다."));
    }
}
