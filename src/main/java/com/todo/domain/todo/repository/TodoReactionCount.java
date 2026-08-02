package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoReactionType;

public interface TodoReactionCount {
    Long getTodoWorkItemId();
    TodoReactionType getReactionType();
    long getReactionCount();
}
