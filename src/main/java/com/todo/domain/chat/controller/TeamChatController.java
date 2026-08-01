package com.todo.domain.chat.controller;

import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.request.MarkAsReadRequest;
import com.todo.domain.chat.dto.request.TypingStatusRequest;
import com.todo.domain.chat.dto.response.ChatUnreadCountResponse;
import com.todo.domain.chat.dto.response.TeamChatMessagePageResponse;
import com.todo.domain.chat.dto.response.TeamChatMessageResponse;
import com.todo.domain.chat.dto.response.TypingStatusResponse;
import com.todo.domain.chat.service.TeamChatService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
public class TeamChatController implements TeamChatControllerDocs {

    private final TeamChatService teamChatService;

    @MessageMapping("/teams/{teamId}/chat")
    @SendTo("/topic/teams/{teamId}")
    public TeamChatMessageResponse handleMessage(
            @DestinationVariable Long teamId,
            @Payload @Valid ChatMessageRequest request,
            Principal principal
    ) {
        return teamChatService.saveMessage(teamId, principal.getName(), request);
    }

    @MessageMapping("/teams/{teamId}/typing")
    @SendTo("/topic/teams/{teamId}/typing")
    public TypingStatusResponse handleTyping(
            @DestinationVariable Long teamId,
            @Payload @Valid TypingStatusRequest request,
            Principal principal
    ) {
        return teamChatService.handleTyping(teamId, principal.getName(), request);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(BusinessException e) {
        return e.getMessage();
    }

    @GetMapping("/{teamId}/chat/messages")
    public ResponseEntity<ApiResponse<TeamChatMessagePageResponse>> getMessages(
            @PathVariable Long teamId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        TeamChatMessagePageResponse response = teamChatService.getMessages(teamId, authentication.getName(), cursorId, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{teamId}/chat/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long teamId,
            @RequestBody @Valid MarkAsReadRequest request,
            Authentication authentication
    ) {
        teamChatService.markAsRead(teamId, authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{teamId}/chat/unread-count")
    public ResponseEntity<ApiResponse<ChatUnreadCountResponse>> getUnreadCount(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        ChatUnreadCountResponse response = teamChatService.getUnreadCount(teamId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
