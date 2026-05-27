package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
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
        String loginId = authentication.getName();
        CreateTodoResponse response = todoService.createTodo(loginId, teamId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "투두가 생성되었습니다."));
    }

    @GetMapping("/teams/{teamId}/todos")
    public ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> getTodoList(
            @PathVariable Long teamId,
            @RequestParam(required = false) String filter,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        List<TodoSummaryResponse> result = todoService.getTodoList(teamId, loginId, filter);
        if (result.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(null, "오늘 할 일이 없습니다"));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/todos/today")
    public ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> getTodayTodoList(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        List<TodoSummaryResponse> result = todoService.getTodayTodoList(teamId, loginId);
        if (result.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(null, "오늘 할 일이 없습니다"));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/todos/history")
    public ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> getTodoHistory(
            @PathVariable Long teamId,
            @RequestParam(required = false) String date,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        List<TodoSummaryResponse> result = todoService.getTodoHistory(teamId, loginId, date);
        if (result.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(null, "해당 날짜의 할 일이 없습니다"));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/todos/report")
    public ResponseEntity<ApiResponse<TodoPeriodReportResponse>> getTodoPeriodReport(
            @PathVariable Long teamId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        TodoPeriodReportResponse response = todoService.getTodoPeriodReport(
                teamId,
                loginId,
                startDate,
                endDate
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/todos/{todoId}")
    public ResponseEntity<ApiResponse<TodoDetailResponse>> getTodoDetail(
            @PathVariable Long todoId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        TodoDetailResponse response = todoService.getTodoDetail(todoId, loginId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/todo-participants/{participantId}/reactions")
    public ResponseEntity<ApiResponse<TodoReactionResponse>> reactTodoParticipant(
            @PathVariable Long participantId,
            @Valid @RequestBody ReactTodoRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        TodoReactionResponse response = todoService.reactTodoParticipant(participantId, loginId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "이모지 반응이 반영되었습니다."));
    }

    @PostMapping("/todos/{todoId}/submit")
    public ResponseEntity<ApiResponse<Void>> submitTodo(
            @PathVariable Long todoId,
            @Valid @RequestBody SubmitTodoRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        todoService.submitTodo(todoId, loginId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "인증 사진이 제출되었습니다."));
    }
}
