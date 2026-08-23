package com.todo.domain.todo.command;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.SlashCommandHandler;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.command.dto.DeadlineApproachingResult;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * `/마감임박` — 지금부터 {@link #WINDOW} 이내에 마감이고 아직 안 끝난 팀 투두를 보여준다.
 */
@Component
@RequiredArgsConstructor
public class DeadlineApproachingCommandHandler implements SlashCommandHandler {

    /**
     * 기존 마감 임박 자동 알림(TodoDeadlineApproachingScheduler)과 같은 30분 기준을 쓴다.
     * 두 기능은 코드 경로가 별개라, 이 값을 조정할 때는 그쪽도 같이 확인해야 한다.
     */
    private static final Duration WINDOW = Duration.ofMinutes(30);

    private final TodoWorkItemRepository todoWorkItemRepository;

    @Override
    public SlashCommand command() {
        return SlashCommand.DEADLINE_APPROACHING;
    }

    @Override
    public Object execute(Team team, User executor) {
        LocalDateTime now = LocalDateTime.now();
        List<TodoWorkItem> workItems = todoWorkItemRepository
                .findByTeamIdAndEffectiveDeadlineBetween(team.getId(), now, now.plus(WINDOW));

        List<DeadlineApproachingResult.Item> items = workItems.stream()
                .filter(workItem -> workItem.getTodo().getStatus() == TodoStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(workItem -> workItem.getTodo().getId()))
                .values().stream()
                .filter(this::hasIncompleteWorkItem)
                .map(this::toItem)
                .sorted(Comparator.comparing(DeadlineApproachingResult.Item::deadline))
                .toList();

        return new DeadlineApproachingResult(items);
    }

    private boolean hasIncompleteWorkItem(List<TodoWorkItem> workItemsOfTodo) {
        return workItemsOfTodo.stream().anyMatch(workItem -> workItem.getStatus() == WorkItemStatus.IN_PROGRESS);
    }

    private DeadlineApproachingResult.Item toItem(List<TodoWorkItem> workItemsOfTodo) {
        Todo todo = workItemsOfTodo.get(0).getTodo();
        LocalDateTime earliestDeadline = workItemsOfTodo.stream()
                .map(TodoWorkItem::getEffectiveDeadline)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        List<String> incompleteAssigneeNicknames = workItemsOfTodo.stream()
                .filter(workItem -> workItem.getStatus() == WorkItemStatus.IN_PROGRESS)
                .map(TodoWorkItem::getAssignee)
                .filter(Objects::nonNull)
                .map(User::getNickname)
                .distinct()
                .toList();

        return new DeadlineApproachingResult.Item(
                todo.getId(),
                todo.getTitle(),
                earliestDeadline.atOffset(ZoneOffset.ofHours(9)),
                incompleteAssigneeNicknames
        );
    }
}
