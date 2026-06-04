package com.todo.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarkAsReadRequest(
        @NotNull
        Long lastReadMessageId
) {}
