package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;

import java.time.LocalDateTime;

public interface TodoWorkItemDetail {
    Long getTodoWorkItemId();
    Long getAssigneeId();
    String getNickname();
    String getProfileImageUrl();
    WorkItemType getType();
    String getTaskTitle();
    String getTaskDescription();
    LocalDateTime getDeadline();
    int getPosition();
    String getProofImageKey();
    String getProofThumbnailKey();
    LocalDateTime getSubmittedAt();
    WorkItemStatus getStatus();
}
