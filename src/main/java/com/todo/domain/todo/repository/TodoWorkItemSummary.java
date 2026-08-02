package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;

public interface TodoWorkItemSummary {
    Long getTodoId();
    Long getAssigneeId();
    String getNickname();
    WorkItemStatus getStatus();
    WorkItemType getType();
    int getPosition();
}
