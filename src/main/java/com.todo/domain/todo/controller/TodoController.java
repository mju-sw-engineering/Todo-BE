package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.EvaluateTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
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
public class TodoController implements TodoControllerDocs {

    private final TodoService todoService;

    @PostMapping("/api/teams/{teamId}/todos")
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

    @GetMapping("/api/teams/{teamId}/todos")
    public ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> getTodoList(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        List<TodoSummaryResponse> result = todoService.getTodoList(teamId, loginId);
        if (result.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(null, "오늘 할 일이 없습니다"));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/api/todos/{todoId}")
    public ResponseEntity<ApiResponse<TodoDetailResponse>> getTodoDetail(
            @PathVariable Long todoId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        TodoDetailResponse response = todoService.getTodoDetail(todoId, loginId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/todos/{todoId}/evaluate")
    public ResponseEntity<ApiResponse<Void>> evaluateTodo(
            @PathVariable Long todoId,
            @Valid @RequestBody EvaluateTodoRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        todoService.evaluateTodo(todoId, loginId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "투표가 완료되었습니다."));
    }

    @PostMapping("/api/todos/{todoId}/submit")
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
