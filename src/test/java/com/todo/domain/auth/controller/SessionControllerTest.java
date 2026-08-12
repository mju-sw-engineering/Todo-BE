package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.response.SessionResponse;
import com.todo.domain.auth.service.SessionService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock
    private SessionService sessionService;

    @Test
    void 세션_목록_응답을_반환한다() {
        SessionController controller = new SessionController(sessionService);
        List<SessionResponse> sessions = List.of(
                new SessionResponse(1L, "device-1",
                        OffsetDateTime.of(2026, 8, 11, 12, 0, 0, 0, ZoneOffset.ofHours(9)),
                        OffsetDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneOffset.ofHours(9)),
                        true)
        );
        given(sessionService.listSessions("user1", "current-token")).willReturn(sessions);

        ResponseEntity<ApiResponse<List<SessionResponse>>> response =
                controller.getSessions(auth(), "current-token");

        assertThat(response.getBody().getData()).isEqualTo(sessions);
    }

    @Test
    void 세션_삭제_응답을_반환한다() {
        SessionController controller = new SessionController(sessionService);

        ResponseEntity<ApiResponse<Void>> response = controller.revokeSession(10L, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("세션을 로그아웃했습니다");
        then(sessionService).should().revokeSession("user1", 10L);
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
