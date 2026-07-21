package com.todo.domain.todo.scheduler;

import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoExpirySchedulerTest {

    @InjectMocks
    private TodoExpiryScheduler todoExpiryScheduler;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TodoParticipantRepository todoParticipantRepository;

    @Test
    void 만료된_투두와_참가자를_동일한_기준시각으로_함께_FAIL_처리한다() {
        todoExpiryScheduler.expireOverdueTodos();

        ArgumentCaptor<LocalDateTime> todoNowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> participantNowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(todoRepository).should().markExpiredTodosAsFail(todoNowCaptor.capture());
        then(todoParticipantRepository).should().markExpiredParticipantsAsFail(participantNowCaptor.capture());

        assertThat(todoNowCaptor.getValue()).isNotNull();
        assertThat(participantNowCaptor.getValue()).isEqualTo(todoNowCaptor.getValue());
    }
}
