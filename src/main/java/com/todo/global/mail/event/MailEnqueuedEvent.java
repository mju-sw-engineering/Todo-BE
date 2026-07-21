package com.todo.global.mail.event;

/**
 * outbox에 메일이 적재됐음을 알리는 이벤트. 적재 트랜잭션 커밋 후 즉시 발송을 시도하는 데 쓰인다.
 */
public record MailEnqueuedEvent(Long outboxId) {
}
