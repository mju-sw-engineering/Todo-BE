package com.todo.domain.todo.recommendation.controller;

import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.recommendation.TeamTodoRecommendationRegisterService;
import com.todo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
public class TeamTodoRecommendationController implements TeamTodoRecommendationControllerDocs {

    private final TeamTodoRecommendationRegisterService registerService;

    @PostMapping("/{teamId}/chat/messages/{messageId}/todo-recommendation/items/{index}/register")
    public ResponseEntity<ApiResponse<CreateTodoResponse>> register(
            @PathVariable Long teamId,
            @PathVariable Long messageId,
            @PathVariable int index,
            Authentication authentication
    ) {
        CreateTodoResponse response = registerService.register(teamId, authentication.getName(), messageId, index);
        return ResponseEntity.ok(ApiResponse.success(response, "추천 할 일을 등록했습니다"));
    }
}
