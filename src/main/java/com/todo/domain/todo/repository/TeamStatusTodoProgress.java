package com.todo.domain.todo.repository;

/** /팀현황 명령어용 — 진행 중인 투두 하나의 WorkItem 완료/전체 개수. */
public interface TeamStatusTodoProgress {
    Long getTodoId();
    String getTitle();
    long getCompletedCount();
    long getTotalCount();
}
