package com.todo.domain.chat.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 핸들러 실행을 별도 스레드로 넘긴다. {@code MailAsyncDispatcher}와 같은 역할이다.
 *
 * <p>핸들러가 WebSocket 인바운드 스레드에서 돌면 핸들러가 끝날 때까지 그 메시지의 브로드캐스트가
 * 나가지 않고, AI 호출처럼 수십 초 걸리는 핸들러는 인바운드 스레드 풀을 고갈시킨다.
 * 새 스레드에는 바인딩된 트랜잭션이 없으므로 {@link SlashCommandDispatchService#executeAndComplete}의
 * {@code @Transactional}이 정상적으로 새 트랜잭션을 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlashCommandAsyncDispatcher {

    private final SlashCommandDispatchService dispatchService;

    @Async("slashCommandTaskExecutor")
    public void dispatch(SlashCommandDispatchEvent event) {
        try {
            dispatchService.executeAndComplete(event);
        } catch (RuntimeException e) {
            // 핸들러 예외든 저장 실패든 실행 트랜잭션은 이미 롤백됐다. 실패만 별도 트랜잭션으로 확정해
            // 칩이 영원히 "처리 중"에 머물지 않게 한다.
            log.warn("슬래시 명령어 실행 실패. command={}, executionId={}",
                    event.command(), event.executionId(), e);
            dispatchService.markFailed(event);
        }
    }
}
