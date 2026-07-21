package com.todo.domain.chat.dto.response;

import com.todo.domain.chat.entity.ChatMessage;

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
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getSender().getProfileImageUrl(),
                message.getContent(),
                message.getCreatedAt().atOffset(ZoneOffset.ofHours(9))
        );
    }
}
