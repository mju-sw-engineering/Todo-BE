package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.ParticipantStatus;

public interface TodoParticipantSummary {
    Long getTodoId();
    Long getUserId();
    ParticipantStatus getStatus();
}
