package com.todo.global.websocket;

import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TeamSubscriptionValidatorTest {

    @InjectMocks
    private TeamSubscriptionValidator validator;

    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;

    @Test
    void 알림_채널_구독은_허용한다() {
        assertThatCode(() -> validator.validate("/user/queue/notifications", "1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 에러_채널_구독은_허용한다() {
        assertThatCode(() -> validator.validate("/user/queue/errors", "1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 팀_채팅_채널은_팀원이면_구독을_허용한다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);

        assertThatCode(() -> validator.validate("/topic/teams/100", "1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 타이핑_채널은_팀원이면_구독을_허용한다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);

        assertThatCode(() -> validator.validate("/topic/teams/100/typing", "1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 판정_채널은_팀원이면_구독을_허용한다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);

        assertThatCode(() -> validator.validate("/topic/teams/100/proof-analyses", "1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 판정_채널도_팀원이_아니면_구독을_거부한다() {
        // 새 접미사가 멤버 검증을 우회하지 않는지 확인한다. 판정 결과에는 제출물 요약이
        // 담기므로 남의 팀 채널을 구독할 수 있으면 팀 내용이 새어나간다.
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> validator.validate("/topic/teams/100/proof-analyses", "1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("해당 채널을 구독할 권한이 없습니다.");
    }

    @Test
    void 등록하지_않은_접미사는_거부한다() {
        // 패턴이 느슨해져 임의 접미사가 통과하면 인가되지 않은 채널이 열린다.
        assertThatThrownBy(() -> validator.validate("/topic/teams/100/proof-analyses/secret", "1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("허용되지 않은 구독 채널입니다.");
        assertThatThrownBy(() -> validator.validate("/topic/teams/100/anything", "1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("허용되지 않은 구독 채널입니다.");
    }

    @Test
    void 팀원이_아니면_구독을_거부한다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> validator.validate("/topic/teams/100", "1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("해당 채널을 구독할 권한이 없습니다.");
    }

    @Test
    void 삭제된_사용자는_팀_채널_구독을_거부한다() {
        given(userRepository.findById(998L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("/topic/teams/100", "998"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void 존재하지_않는_팀_채널도_권한_없음으로_동일하게_거부한다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(999L, 1L)).willReturn(false);

        assertThatThrownBy(() -> validator.validate("/topic/teams/999", "1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("해당 채널을 구독할 권한이 없습니다.");
    }

    @Test
    void 허용되지_않은_경로는_구독을_거부한다() {
        assertThatThrownBy(() -> validator.validate("/topic/todos/10", "1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("허용되지 않은 구독 채널입니다.");
    }

    @Test
    void destination이_null이면_구독을_거부한다() {
        assertThatThrownBy(() -> validator.validate(null, "1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("허용되지 않은 구독 채널입니다.");
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임" + id, "profiles/" + id + ".png");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
