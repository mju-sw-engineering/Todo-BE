package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.service.TodoService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams/{teamId}/todos")
@RequiredArgsConstructor
public class TodoController implements TodoControllerDocs {

    private final TodoService todoService;

    @PostMapping
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
}
