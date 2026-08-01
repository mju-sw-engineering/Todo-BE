package com.todo.domain.chat.dto.response;

import com.todo.domain.chat.entity.ChatMessage;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.support.WithdrawnUserDisplay;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ChatMessageResponse(
        Long messageId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        String content,
        OffsetDateTime createdAt
) {
    /**
     * 발신자가 탈퇴하면 sender가 null이다. 메시지는 팀 공동 기록이므로 유지하고 작성자만 익명으로 표시한다.
     */
    public static ChatMessageResponse from(ChatMessage message) {
        User sender = message.getSender();

        return new ChatMessageResponse(
                message.getId(),
                sender == null ? null : sender.getId(),
                sender == null ? WithdrawnUserDisplay.NICKNAME : sender.getNickname(),
                sender == null ? null : sender.getProfileImageUrl(),
                message.getContent(),
                message.getCreatedAt().atOffset(ZoneOffset.ofHours(9))
        );
    }
}
