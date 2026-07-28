package com.todo.domain.chat.dto.response;

import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.user.entity.User;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record TeamChatMessageResponse(
        Long messageId,
        Long teamId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        String content,
        boolean isBot,
        OffsetDateTime createdAt
) {
    public static TeamChatMessageResponse from(TeamChatMessage message) {
        User sender = message.getSender();
        return new TeamChatMessageResponse(
                message.getId(),
                message.getTeam().getId(),
                sender != null ? sender.getId() : null,
                sender != null ? sender.getNickname() : null,
                sender != null ? sender.getProfileImageUrl() : null,
                message.getContent(),
                message.isBot(),
                message.getCreatedAt().atOffset(ZoneOffset.ofHours(9))
        );
    }
}
