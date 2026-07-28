package com.todo.domain.chat.dto.response;

import java.util.List;

public record TeamChatMessagePageResponse(
        List<TeamChatMessageResponse> messages,
        boolean hasNext,
        Long nextCursorId
) {}
