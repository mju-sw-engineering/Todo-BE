package com.todo.global.websocket;

import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TodoSubscriptionValidator {

    private static final Pattern TODO_PATTERN = Pattern.compile("^/topic/todos/(\\d+)(?:/.*)?$");

    private final TodoRepository todoRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public void validate(String destination, String loginId) {
        if (destination == null) {
            return;
        }
        Matcher matcher = TODO_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        Long todoId = Long.parseLong(matcher.group(1));

        Todo todo = todoRepository.findByIdWithCreatorAndTeam(todoId)
                .orElseThrow(() -> new MessageDeliveryException("존재하지 않는 투두입니다."));

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new MessageDeliveryException("사용자를 찾을 수 없습니다."));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), user.getId())) {
            throw new MessageDeliveryException("해당 채널을 구독할 권한이 없습니다.");
        }
    }
}
