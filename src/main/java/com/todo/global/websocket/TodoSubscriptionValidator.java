package com.todo.global.websocket;

import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TodoSubscriptionValidator {

    private static final Pattern TODO_PATTERN = Pattern.compile("^/topic/todos/(\\d+)(?:/typing)?$");
    private static final Set<String> ALLOWED_USER_DESTINATIONS = Set.of(
            "/user/queue/notifications",
            "/user/queue/errors"
    );

    private final TodoRepository todoRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public void validate(String destination, String loginId) {
        if (destination == null) {
            throw new MessageDeliveryException("허용되지 않은 구독 채널입니다.");
        }
        if (ALLOWED_USER_DESTINATIONS.contains(destination)) {
            return;
        }

        Matcher matcher = TODO_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw new MessageDeliveryException("허용되지 않은 구독 채널입니다.");
        }

        Long todoId = parseTodoId(matcher.group(1));

        Todo todo = todoRepository.findByIdWithCreatorAndTeam(todoId)
                .orElseThrow(() -> new MessageDeliveryException("존재하지 않는 투두입니다."));

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new MessageDeliveryException("사용자를 찾을 수 없습니다."));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), user.getId())) {
            throw new MessageDeliveryException("해당 채널을 구독할 권한이 없습니다.");
        }
    }

    private Long parseTodoId(String rawTodoId) {
        try {
            return Long.parseLong(rawTodoId);
        } catch (NumberFormatException e) {
            throw new MessageDeliveryException("유효하지 않은 투두 채널입니다.");
        }
    }
}
