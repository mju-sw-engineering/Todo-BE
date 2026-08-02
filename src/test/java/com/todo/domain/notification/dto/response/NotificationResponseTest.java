package com.todo.domain.notification.dto.response;

import com.todo.domain.notification.entity.Notification;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationActorText;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    @Test
    void 자리표시자를_행위자의_현재_닉네임으로_치환한다() {
        User actor = user("윤진");
        Notification notification = notification(actor, "새로운 투두", template("님이 '발표'을(를) 만들었습니다."));

        NotificationResponse response = NotificationResponse.from(notification);

        assertThat(response.content()).isEqualTo("윤진님이 '발표'을(를) 만들었습니다.");
    }

    @Test
    void 행위자가_닉네임을_바꾸면_기존_알림도_새_닉네임으로_보인다() {
        User actor = user("윤진");
        Notification notification = notification(actor, "새로운 투두", template("님이 만들었습니다."));

        actor.updateNickname("종혁");

        assertThat(NotificationResponse.from(notification).content()).isEqualTo("종혁님이 만들었습니다.");
    }

    @Test
    void 행위자가_탈퇴하면_탈퇴한_사용자로_보인다() {
        Notification notification = notification(null, "새로운 투두", template("님이 만들었습니다."));

        assertThat(NotificationResponse.from(notification).content()).isEqualTo("탈퇴한 사용자님이 만들었습니다.");
    }

    @Test
    void 제목에_있는_자리표시자도_치환한다() {
        Notification notification = notification(user("윤진"), template("님이 배정했습니다."), "내용");

        assertThat(NotificationResponse.from(notification).title()).isEqualTo("윤진님이 배정했습니다.");
    }

    @Test
    void 자리표시자가_없는_기존_문구는_그대로_내려간다() {
        Notification notification = notification(null, "새로운 투두", "윤진님이 만들었습니다.");

        NotificationResponse response = NotificationResponse.from(notification);

        assertThat(response.title()).isEqualTo("새로운 투두");
        assertThat(response.content()).isEqualTo("윤진님이 만들었습니다.");
    }

    private String template(String suffix) {
        return NotificationActorText.PLACEHOLDER + suffix;
    }

    private User user(String nickname) {
        return User.create("login-" + nickname, "pw", nickname, null);
    }

    private Notification notification(User actor, String title, String content) {
        Notification notification = Notification.create(
                user("수신자"), actor, NotificationType.TODO_CREATED, title, content, 10L
        );
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 8, 2, 12, 0));
        return notification;
    }
}
