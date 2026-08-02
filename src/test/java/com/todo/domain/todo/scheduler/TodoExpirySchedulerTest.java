package com.todo.domain.todo.scheduler;

import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoExpirySchedulerTest {

    @InjectMocks
    private TodoExpiryScheduler todoExpiryScheduler;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;

    @Test
    void 유효_마감이_지난_WorkItem의_부모_Todo만_FAIL_처리하고_다른_항목은_유지한다() {
        given(todoWorkItemRepository.findOverdueTodoIds(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .willReturn(List.of(10L));

        todoExpiryScheduler.expireOverdueTodos();

        ArgumentCaptor<LocalDateTime> overdueLookupNowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> workItemNowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(todoWorkItemRepository).should().findOverdueTodoIds(overdueLookupNowCaptor.capture());
        then(todoWorkItemRepository).should().markOverdueAsFail(workItemNowCaptor.capture());
        then(todoRepository).should().markAsFailByIds(List.of(10L));

        assertThat(overdueLookupNowCaptor.getValue()).isNotNull();
        assertThat(workItemNowCaptor.getValue()).isEqualTo(overdueLookupNowCaptor.getValue());
    }
}
