package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoStatus;

import java.time.LocalDateTime;

public interface TodoDailyEvaluationStat {

    String getTitle();

    TodoStatus getStatus();

    LocalDateTime getDeadline();

    long getAchievementCount();

    long getParticipantCount();
}
