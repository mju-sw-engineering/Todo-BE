package com.todo.domain.chat.controller;

import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.request.MarkAsReadRequest;
import com.todo.domain.chat.dto.response.TeamChatMessagePageResponse;
import com.todo.domain.chat.dto.response.TeamChatMessageResponse;
import com.todo.domain.chat.service.TeamChatService;
import com.todo.global.exception.BusinessException;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TeamChatControllerTest {

    @Mock
    private TeamChatService teamChatService;

    @Test
    void 웹소켓_메시지를_저장하고_반환한다() {
        TeamChatController controller = new TeamChatController(teamChatService);
        ChatMessageRequest request = new ChatMessageRequest("안녕");
        TeamChatMessageResponse serviceResponse =
                new TeamChatMessageResponse(1L, 100L, 1L, "닉네임", null, "안녕", false, null);
        Principal principal = () -> "user1";
        given(teamChatService.saveMessage(100L, "user1", request)).willReturn(serviceResponse);

        TeamChatMessageResponse response = controller.handleMessage(100L, request, principal);

        assertThat(response).isEqualTo(serviceResponse);
    }

    @Test
    void 비즈니스_예외_메시지를_사용자에게_반환한다() {
        TeamChatController controller = new TeamChatController(teamChatService);

        String response = controller.handleException(new BusinessException("권한 없음", HttpStatus.FORBIDDEN));

        assertThat(response).isEqualTo("권한 없음");
    }

    @Test
    void 읽음처리는_PATCH로_매핑되고_서비스에_위임한다() throws NoSuchMethodException {
        TeamChatController controller = new TeamChatController(teamChatService);
        MarkAsReadRequest request = new MarkAsReadRequest(500L);

        controller.markAsRead(100L, request, new TestingAuthenticationToken("user1", null));

        then(teamChatService).should().markAsRead(100L, "user1", request);
        PatchMapping mapping = TeamChatController.class
                .getMethod("markAsRead", Long.class, MarkAsReadRequest.class, Authentication.class)
                .getAnnotation(PatchMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/{teamId}/chat/read");
    }

    @Test
    void 채팅_메시지_목록_응답을_반환한다() {
        TeamChatController controller = new TeamChatController(teamChatService);
        TeamChatMessagePageResponse serviceResponse = new TeamChatMessagePageResponse(List.of(), false, null);
        given(teamChatService.getMessages(100L, "user1", 99L, 20)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TeamChatMessagePageResponse>> response =
                controller.getMessages(100L, 99L, 20, new TestingAuthenticationToken("user1", null));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }
}
