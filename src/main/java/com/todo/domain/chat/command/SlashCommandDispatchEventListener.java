package com.todo.domain.chat.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 슬래시 명령어 실행 트랜잭션이 커밋된 뒤에만 핸들러를 실행한다.
 * 롤백된 채팅 메시지에 대한 명령어가 실행되는 것을 막는다.
 */
@Component
@RequiredArgsConstructor
public class SlashCommandDispatchEventListener {

    private final SlashCommandDispatchService dispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlashCommandDispatch(SlashCommandDispatchEvent event) {
        dispatchService.completeExecution(event);
    }
}
