package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CheckInRequest;
import com.todo.domain.todo.dto.response.CheckInResponse;
import com.todo.domain.todo.service.WorkItemCheckInService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todo-work-items/{workItemId}/check-ins")
public class CheckInController implements CheckInControllerDocs {

    private final WorkItemCheckInService workItemCheckInService;

    @PostMapping
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(
            @PathVariable Long workItemId,
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        CheckInResponse response = workItemCheckInService.checkIn(loginId, workItemId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "체크인이 등록되었습니다."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CheckInResponse>>> getCheckIns(
            @PathVariable Long workItemId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        List<CheckInResponse> response = workItemCheckInService.getCheckIns(loginId, workItemId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
