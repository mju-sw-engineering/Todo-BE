package com.todo.domain.chat.command;

import java.util.Arrays;
import java.util.Optional;

/**
 * 팀 채팅에서 인식하는 슬래시 명령어 목록. 채팅 메시지 내용이 {@link #commandText()}와
 * 정확히 일치할 때만 명령어로 인식한다(오타 등은 그냥 일반 메시지).
 *
 * <p>실제 조회/추천 로직은 여기 없다 — {@link SlashCommandHandler} 구현체(#194, #195)가
 * {@link SlashCommandRegistry}에 등록되어야 이 명령어가 "살아난다." 이 이슈(#193)가 머지되는
 * 시점엔 등록된 핸들러가 없어 아래 값들과 일치해도 아무 일도 일어나지 않는다.
 */
public enum SlashCommand {
    MY_TODOS("/내할일", SlashCommandScope.PERSONAL),
    DEADLINE_APPROACHING("/마감임박", SlashCommandScope.TEAM),
    TEAM_STATUS("/팀현황", SlashCommandScope.TEAM),
    TODO_RECOMMENDATION("/할일추천", SlashCommandScope.PERSONAL);

    private final String commandText;
    private final SlashCommandScope scope;

    SlashCommand(String commandText, SlashCommandScope scope) {
        this.commandText = commandText;
        this.scope = scope;
    }

    public String commandText() {
        return commandText;
    }

    public SlashCommandScope scope() {
        return scope;
    }

    public static Optional<SlashCommand> fromText(String text) {
        return Arrays.stream(values())
                .filter(command -> command.commandText.equals(text))
                .findFirst();
    }
}
