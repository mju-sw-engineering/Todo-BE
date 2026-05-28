package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoReactionType;

public interface TodoReactionCount {
    Long getTodoParticipantId();
    TodoReactionType getReactionType();
    long getReactionCount();
}
