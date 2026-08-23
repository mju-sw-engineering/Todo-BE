package com.todo.domain.todo.command.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DeadlineApproachingResult(List<Item> items) {

    public record Item(
            Long todoId,
            String todoTitle,
            OffsetDateTime deadline,
            List<String> incompleteAssigneeNicknames
    ) {
    }
}
