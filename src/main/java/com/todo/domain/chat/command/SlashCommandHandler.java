package com.todo.domain.chat.command;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;

/**
 * 슬래시 명령어 하나의 실제 처리 로직. 구현체는 각자 도메인 서비스를 의존성으로 갖고
 * (예: TodoRepository, AI 클라이언트) {@link SlashCommandRegistry}에 스프링 빈으로만
 * 등록하면 된다 — 디스패치 인프라는 이 인터페이스만 안다.
 *
 * <p>반환값은 {@link SlashCommandDispatchEventListener}가 JSON으로 직렬화해 저장하므로
 * 모양에 제약이 없다.
 */
public interface SlashCommandHandler {

    SlashCommand command();

    Object execute(Team team, User executor);
}
