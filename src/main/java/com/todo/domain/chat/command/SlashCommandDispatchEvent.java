package com.todo.domain.chat.command;

/**
 * 슬래시 명령어 실행을 트랜잭션 커밋 후로 미루기 위한 이벤트. 커밋 후 시점에는 원래
 * 트랜잭션의 영속성 컨텍스트가 닫혀 있을 수 있어 엔티티가 아니라 ID만 담는다.
 */
public record SlashCommandDispatchEvent(
        Long executionId,
        Long teamId,
        Long executorId,
        Long chatMessageId,
        SlashCommand command
) {
}
