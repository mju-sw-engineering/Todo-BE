package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.response.SessionResponse;
import com.todo.domain.auth.service.SessionService;
import com.todo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
public class SessionController implements SessionControllerDocs {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions(
            Authentication authentication,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        String userId = authentication.getName();
        List<SessionResponse> sessions = sessionService.listSessions(userId, refreshToken);
        return ResponseEntity.ok(ApiResponse.success(sessions, "세션 목록을 조회했습니다"));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        sessionService.revokeSession(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(null, "세션을 로그아웃했습니다"));
    }
}
