package com.todo.domain.chat.controller;

import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.request.MarkAsReadRequest;
import com.todo.domain.chat.dto.request.TypingStatusRequest;
import com.todo.domain.chat.dto.response.ChatMessagePageResponse;
import com.todo.domain.chat.dto.response.ChatMessageResponse;
import com.todo.domain.chat.dto.response.ChatUnreadCountResponse;
import com.todo.domain.chat.dto.response.TypingStatusResponse;
import com.todo.domain.chat.service.ChatService;
import com.todo.global.exception.BusinessException;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
public class ChatController implements ChatControllerDocs {

    private final ChatService chatService;

    @MessageMapping("/todos/{todoId}/chat")
    @SendTo("/topic/todos/{todoId}")
    public ChatMessageResponse handleMessage(
            @DestinationVariable Long todoId,
            @Payload @Valid ChatMessageRequest request,
            Principal principal
    ) {
        return chatService.saveMessage(todoId, principal.getName(), request);
    }

    @MessageMapping("/todos/{todoId}/typing")
    @SendTo("/topic/todos/{todoId}/typing")
    public TypingStatusResponse handleTyping(
            @DestinationVariable Long todoId,
            @Payload @Valid TypingStatusRequest request,
            Principal principal
    ) {
        return chatService.handleTyping(todoId, principal.getName(), request);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(BusinessException e) {
        return e.getMessage();
    }

    @GetMapping("/api/todos/{todoId}/chat/messages")
    public ResponseEntity<ApiResponse<ChatMessagePageResponse>> getMessages(
            @PathVariable Long todoId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        ChatMessagePageResponse response = chatService.getMessages(todoId, loginId, cursorId, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/todos/{todoId}/chat/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long todoId,
            @RequestBody @Valid MarkAsReadRequest request,
            Authentication authentication
    ) {
        chatService.markAsRead(todoId, authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/api/todos/{todoId}/chat/unread-count")
    public ResponseEntity<ApiResponse<ChatUnreadCountResponse>> getUnreadCount(
            @PathVariable Long todoId,
            Authentication authentication
    ) {
        ChatUnreadCountResponse response = chatService.getUnreadCount(todoId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
