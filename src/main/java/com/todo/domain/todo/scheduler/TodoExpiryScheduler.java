package com.todo.domain.todo.scheduler;

import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 마감 시간이 지난 WorkItem과 부모 Todo를 주기적으로 FAIL 처리한다.
 * 기존에는 조회 경로에서 매번 갱신했으나, 조회를 읽기 전용으로 유지하기 위해 스케줄러로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class TodoExpiryScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;

    @Scheduled(fixedDelayString = "${todo.scheduling.expiry-interval-ms:60000}")
    @Transactional
    public void expireOverdueTodos() {
        LocalDateTime now = LocalDateTime.now(KST);
        List<Long> overdueTodoIds = todoWorkItemRepository.findOverdueTodoIds(now);
        if (overdueTodoIds.isEmpty()) {
            return;
        }
        todoWorkItemRepository.markOverdueAsFail(now);
        todoRepository.markAsFailByIds(overdueTodoIds);
    }
}
