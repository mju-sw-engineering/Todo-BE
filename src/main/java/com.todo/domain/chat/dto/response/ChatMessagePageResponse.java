package com.todo.domain.chat.dto.response;

import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> messages,
        boolean hasNext,
        Long nextCursorId
) {
}
