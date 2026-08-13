package com.todo.domain.todo.repository;

public interface TodoWorkItemNotificationInfo {
    Long getWorkItemId();
    Long getTodoId();
    String getTodoTitle();
    Long getAssigneeId();
}
