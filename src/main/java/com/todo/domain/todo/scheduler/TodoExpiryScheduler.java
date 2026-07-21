package com.todo.domain.todo.scheduler;

import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 마감 시간이 지난 투두와 그 참가자를 주기적으로 FAIL 처리한다.
 * 기존에는 조회 경로에서 매번 갱신했으나, 조회를 읽기 전용으로 유지하기 위해 스케줄러로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class TodoExpiryScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TodoRepository todoRepository;
    private final TodoParticipantRepository todoParticipantRepository;

    @Scheduled(fixedDelayString = "${todo.scheduling.expiry-interval-ms:60000}")
    @Transactional
    public void expireOverdueTodos() {
        LocalDateTime now = LocalDateTime.now(KST);
        todoRepository.markExpiredTodosAsFail(now);
        todoParticipantRepository.markExpiredParticipantsAsFail(now);
    }
}
