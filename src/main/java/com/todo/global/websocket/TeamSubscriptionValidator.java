package com.todo.global.websocket;

import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.user.repository.UserRepository;
import com.todo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TeamSubscriptionValidator {

    private static final Pattern TEAM_PATTERN = Pattern.compile("^/topic/teams/(\\d+)(?:/typing)?$");
    private static final Set<String> ALLOWED_USER_DESTINATIONS = Set.of(
            "/user/queue/notifications",
            "/user/queue/errors"
    );

    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public void validate(String destination, String loginId) {
        if (destination == null) {
            throw new MessageDeliveryException("허용되지 않은 구독 채널입니다.");
        }
        if (ALLOWED_USER_DESTINATIONS.contains(destination)) {
            return;
        }

        Matcher matcher = TEAM_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw new MessageDeliveryException("허용되지 않은 구독 채널입니다.");
        }

        Long teamId = parseTeamId(matcher.group(1));

        // 팀 존재 확인은 하지 않는다. 없는 팀이면 아래 멤버 확인이 어차피 실패하고,
        // 응답 메시지가 갈리면 인증된 사용자가 팀 ID를 열거해 존재 여부를 알아낼 수 있다.
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new MessageDeliveryException("사용자를 찾을 수 없습니다."));

        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new MessageDeliveryException("해당 채널을 구독할 권한이 없습니다.");
        }
    }

    private Long parseTeamId(String rawTeamId) {
        try {
            return Long.parseLong(rawTeamId);
        } catch (NumberFormatException e) {
            throw new MessageDeliveryException("유효하지 않은 팀 채널입니다.");
        }
    }
}
