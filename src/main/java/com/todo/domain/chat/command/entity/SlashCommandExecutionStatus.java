package com.todo.domain.chat.command.entity;

public enum SlashCommandExecutionStatus {
    PENDING,
    DONE,
    /** 핸들러 실행 실패 또는 비동기 큐 포화. 재시도하지 않으며, 사용자는 명령어를 다시 치면 된다. */
    FAILED
}
