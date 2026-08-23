package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.WorkItemStatus;

public interface WorkItemStatusCount {
    WorkItemStatus getStatus();
    long getCount();
}
