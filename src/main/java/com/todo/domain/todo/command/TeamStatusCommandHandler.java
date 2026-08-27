package com.todo.domain.todo.command;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.SlashCommandHandler;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.command.dto.TeamStatusResult;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TeamStatusTodoProgress;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoStatusCount;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * `/팀현황` — 팀 전체 투두를 상태별로 집계하고, 그중 진행 중인 투두들을 각각 WorkItem
 * 완료/전체 개수와 함께 보여준다. 팀원별 개인 완료 현황은 다루지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TeamStatusCommandHandler implements SlashCommandHandler {

    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;

    @Override
    public SlashCommand command() {
        return SlashCommand.TEAM_STATUS;
    }

    @Override
    public Object execute(Team team, User executor) {
        Map<TodoStatus, Long> todoCounts = todoRepository.countByTeamIdGroupByStatus(team.getId()).stream()
                .collect(Collectors.toMap(TodoStatusCount::getStatus, TodoStatusCount::getCount));

        var inProgressTodos = todoWorkItemRepository.findInProgressTodoProgressByTeamId(team.getId()).stream()
                .map(this::toInProgressTodo)
                .toList();

        return new TeamStatusResult(
                todoCounts.getOrDefault(TodoStatus.IN_PROGRESS, 0L),
                todoCounts.getOrDefault(TodoStatus.SUCCESS, 0L),
                todoCounts.getOrDefault(TodoStatus.FAIL, 0L),
                inProgressTodos
        );
    }

    private TeamStatusResult.InProgressTodo toInProgressTodo(TeamStatusTodoProgress progress) {
        return new TeamStatusResult.InProgressTodo(
                progress.getTodoId(),
                progress.getTitle(),
                progress.getCompletedCount(),
                progress.getTotalCount()
        );
    }
}
