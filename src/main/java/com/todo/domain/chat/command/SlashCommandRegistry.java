package com.todo.domain.chat.command;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 등록된 {@link SlashCommandHandler} 빈을 명령어별로 찾아준다. 이 이슈(#193)가 머지되는
 * 시점엔 등록된 핸들러가 없을 수 있다 — #194/#195이 핸들러 빈을 추가하면 그 순간부터
 * {@link #findHandler(SlashCommand)}가 값을 반환하기 시작한다.
 */
@Component
public class SlashCommandRegistry {

    private final Map<SlashCommand, SlashCommandHandler> handlers;

    public SlashCommandRegistry(List<SlashCommandHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(SlashCommandHandler::command, Function.identity()));
    }

    public Optional<SlashCommandHandler> findHandler(SlashCommand command) {
        return Optional.ofNullable(handlers.get(command));
    }
}
