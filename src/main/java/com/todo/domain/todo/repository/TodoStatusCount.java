package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoStatus;

public interface TodoStatusCount {
    TodoStatus getStatus();
    long getCount();
}
