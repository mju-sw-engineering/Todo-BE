package com.todo.domain.chat.controller;

import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.response.ChatMessagePageResponse;
import com.todo.domain.chat.dto.response.ChatMessageResponse;
import com.todo.domain.chat.service.ChatService;
import com.todo.global.exception.BusinessException;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Test
    void 웹소켓_메시지를_저장하고_반환한다() {
        ChatController controller = new ChatController(chatService);
        ChatMessageRequest request = new ChatMessageRequest("안녕");
        ChatMessageResponse serviceResponse = new ChatMessageResponse(1L, 1L, "닉네임", null, "안녕", null);
        Principal principal = () -> "user1";
        given(chatService.saveMessage(10L, "user1", request)).willReturn(serviceResponse);

        ChatMessageResponse response = controller.handleMessage(10L, request, principal);

        assertThat(response).isEqualTo(serviceResponse);
    }

    @Test
    void 비즈니스_예외_메시지를_사용자에게_반환한다() {
        ChatController controller = new ChatController(chatService);

        String response = controller.handleException(new BusinessException("권한 없음", HttpStatus.FORBIDDEN));

        assertThat(response).isEqualTo("권한 없음");
    }

    @Test
    void 채팅_메시지_목록_응답을_반환한다() {
        ChatController controller = new ChatController(chatService);
        ChatMessagePageResponse serviceResponse = new ChatMessagePageResponse(List.of(), false, null);
        given(chatService.getMessages(10L, "user1", 99L, 20)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<ChatMessagePageResponse>> response =
                controller.getMessages(10L, 99L, 20, new TestingAuthenticationToken("user1", null));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }
}
