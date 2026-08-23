package com.todo.domain.chat.controller;

import com.todo.domain.chat.command.dto.response.SlashCommandResultResponse;
import com.todo.domain.chat.dto.request.MarkAsReadRequest;
import com.todo.domain.chat.dto.response.ChatUnreadCountResponse;
import com.todo.domain.chat.dto.response.TeamChatMessagePageResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Chat", description = "팀 실시간 채팅 API")
public interface TeamChatControllerDocs {

    @Operation(
            summary = "채팅 내역 조회",
            description = "팀 채팅 내역을 커서 기반으로 조회합니다. " +
                    "cursorId 없으면 최신 메시지부터, 있으면 해당 id 이전 메시지를 반환합니다."
    )
    ResponseEntity<ApiResponse<TeamChatMessagePageResponse>> getMessages(
            @Parameter(description = "팀 ID") Long teamId,
            @Parameter(description = "커서 ID (이전 메시지 조회 시 마지막 messageId)") Long cursorId,
            @Parameter(description = "조회 개수 (기본값: 20)") int size,
            Authentication authentication
    );

    @Operation(
            summary = "채팅 읽음 처리",
            description = "마지막으로 읽은 메시지 ID를 저장합니다. 채팅방 입장 시 또는 메시지 확인 시 호출합니다."
    )
    ResponseEntity<ApiResponse<Void>> markAsRead(
            @Parameter(description = "팀 ID") Long teamId,
            MarkAsReadRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "안 읽은 메시지 수 조회",
            description = "마지막으로 읽은 메시지 이후의 안 읽은 메시지 수를 반환합니다."
    )
    ResponseEntity<ApiResponse<ChatUnreadCountResponse>> getUnreadCount(
            @Parameter(description = "팀 ID") Long teamId,
            Authentication authentication
    );

    @Operation(
            summary = "슬래시 명령어 실행 결과 조회",
            description = "채팅 메시지 하나에 달린 슬래시 명령어 실행 결과를 조회합니다. " +
                    "개인용(PERSONAL) 명령어는 실행자 본인만 조회할 수 있습니다."
    )
    ResponseEntity<ApiResponse<SlashCommandResultResponse>> getCommandResult(
            @Parameter(description = "팀 ID") Long teamId,
            @Parameter(description = "명령어를 촉발한 채팅 메시지 ID") Long messageId,
            Authentication authentication
    );
}
