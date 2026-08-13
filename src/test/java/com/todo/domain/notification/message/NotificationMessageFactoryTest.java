package com.todo.domain.notification.message;

import com.todo.domain.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * yml에 정의한 문구가 실제로 바인딩되는지까지 확인해야 하므로 컨텍스트를 띄운다.
 * 단위 테스트로 properties를 손으로 채우면 yml 오타를 잡지 못한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationMessageFactoryTest {

    @Autowired
    private NotificationMessageFactory factory;

    @Autowired
    private NotificationMessageProperties properties;

    @Test
    void 모든_알림_종류에_문구가_정의되어_있다() {
        assertThat(properties.getMessages().keySet())
                .describedAs("enum에 종류만 추가하고 yml을 빠뜨리면 발송 시점에야 터진다")
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(NotificationType.values()));
    }

    @Test
    void 투두_생성_문구는_제목을_채우고_행위자는_자리표시자로_남긴다() {
        NotificationMessage message = factory.todoCreated("기말 발표");

        assertThat(message.type()).isEqualTo(NotificationType.TODO_CREATED);
        assertThat(message.title()).isEqualTo("새로운 투두가 생성되었습니다.");
        assertThat(message.content()).isEqualTo("{actor}님이 '기말 발표'을(를) 만들었습니다.");
    }

    @Test
    void 저장되는_문구에는_행위자_자리표시자가_남아있다() {
        assertThat(factory.todoCreated("기말 발표").content())
                .describedAs("이름을 박아 저장하면 닉네임 변경·탈퇴 후 옛 이름이 남는다")
                .contains(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 할일_배정_문구는_투두별_묶음_개수를_채우고_행위자는_자리표시자로_남긴다() {
        NotificationMessage message = factory.todoAssigned("기말 발표", 2);

        assertThat(message.type()).isEqualTo(NotificationType.TODO_ASSIGNED);
        assertThat(message.title()).isEqualTo("새로운 할 일이 배정되었습니다.");
        assertThat(message.content()).isEqualTo("{actor}님이 '기말 발표'의 할 일 2개를 배정했습니다.");
        assertThat(message.content()).contains(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 할일_미배정_문구는_투두별_묶음_개수를_채우고_행위자는_자리표시자로_남긴다() {
        NotificationMessage message = factory.todoUnassigned("기말 발표", 3);

        assertThat(message.type()).isEqualTo(NotificationType.TODO_UNASSIGNED);
        assertThat(message.title()).isEqualTo("할 일이 미배정 상태가 되었습니다.");
        assertThat(message.content()).isEqualTo("{actor}님이 '기말 발표'의 할 일 3개를 미배정으로 변경했습니다.");
        assertThat(message.content()).contains(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 채팅_문구는_발신자_닉네임을_그대로_채운다() {
        NotificationMessage message = factory.chatMessage("윤진", "안녕하세요");

        assertThat(message.type()).isEqualTo(NotificationType.CHAT_MESSAGE);
        assertThat(message.title()).isEqualTo("윤진님이 메시지를 보냈습니다.");
        assertThat(message.content()).isEqualTo("안녕하세요");
        assertThat(message.title())
                .describedAs("push 전용이라 저장되지 않으므로 자리표시자가 필요 없다")
                .doesNotContain(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 값에_포함된_다른_자리표시자는_그대로_둔다() {
        NotificationMessage message = factory.todoCreated("{senderNickname} 정리");

        assertThat(message.content())
                .describedAs("치환은 넘긴 키만 대상으로 한다. 값에 든 문자열을 다시 해석하지 않는다")
                .isEqualTo("{actor}님이 '{senderNickname} 정리'을(를) 만들었습니다.");
    }

    @Test
    void 인증사진_제출_문구는_투두_제목을_채운다() {
        NotificationMessage message = factory.todoSubmitted("기말 발표");

        assertThat(message.type()).isEqualTo(NotificationType.TODO_SUBMITTED);
        assertThat(message.content()).contains("기말 발표");
    }

    @Test
    void 마감_임박_문구는_투두_제목을_채운다() {
        NotificationMessage message = factory.todoDeadlineApproaching("기말 발표");

        assertThat(message.type()).isEqualTo(NotificationType.TODO_DEADLINE_APPROACHING);
        assertThat(message.content()).contains("기말 발표");
    }

    @Test
    void 마감_초과_실패_문구는_투두_제목을_채운다() {
        NotificationMessage message = factory.todoWorkItemExpired("기말 발표");

        assertThat(message.type()).isEqualTo(NotificationType.TODO_WORK_ITEM_EXPIRED);
        assertThat(message.content()).contains("기말 발표");
    }

    @Test
    void 반응_추가_문구는_투두_제목을_채우고_행위자는_자리표시자로_남긴다() {
        NotificationMessage message = factory.todoReactionAdded("기말 발표");

        assertThat(message.type()).isEqualTo(NotificationType.TODO_REACTION_ADDED);
        assertThat(message.content()).contains("기말 발표");
        assertThat(message.content()).contains(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 전원_완료_문구는_투두_제목을_채운다() {
        NotificationMessage message = factory.todoAllCompleted("기말 발표");

        assertThat(message.type()).isEqualTo(NotificationType.TODO_ALL_COMPLETED);
        assertThat(message.content()).contains("기말 발표");
    }

    @Test
    void 팀원_합류_문구는_행위자를_자리표시자로_남긴다() {
        NotificationMessage message = factory.teamMemberJoined();

        assertThat(message.type()).isEqualTo(NotificationType.TEAM_MEMBER_JOINED);
        assertThat(message.content()).contains(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 팀원_탈퇴_문구는_행위자를_자리표시자로_남긴다() {
        NotificationMessage message = factory.teamMemberLeft();

        assertThat(message.type()).isEqualTo(NotificationType.TEAM_MEMBER_LEFT);
        assertThat(message.content()).contains(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 강퇴_문구는_행위자를_자리표시자로_남긴다() {
        NotificationMessage message = factory.teamMemberRemoved();

        assertThat(message.type()).isEqualTo(NotificationType.TEAM_MEMBER_REMOVED);
        assertThat(message.content()).contains(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 팀장_이양_문구는_행위자_자리표시자가_없다() {
        NotificationMessage message = factory.teamLeaderChanged();

        assertThat(message.type()).isEqualTo(NotificationType.TEAM_LEADER_CHANGED);
        assertThat(message.content())
                .describedAs("시스템 알림이라 행위자가 없다")
                .doesNotContain(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 새기기_로그인_문구는_행위자_자리표시자가_없다() {
        NotificationMessage message = factory.newDeviceLogin();

        assertThat(message.type()).isEqualTo(NotificationType.NEW_DEVICE_LOGIN);
        assertThat(message.content()).doesNotContain(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 비밀번호_변경_문구는_행위자_자리표시자가_없다() {
        NotificationMessage message = factory.passwordChanged();

        assertThat(message.type()).isEqualTo(NotificationType.PASSWORD_CHANGED);
        assertThat(message.content()).doesNotContain(NotificationActorText.PLACEHOLDER);
    }

    @Test
    void 투표_생성_문구는_투표_제목을_채우고_행위자는_자리표시자로_남긴다() {
        NotificationMessage message = factory.availabilityPollCreated("주간 회의 시간 조율");

        assertThat(message.type()).isEqualTo(NotificationType.AVAILABILITY_POLL_CREATED);
        assertThat(message.content()).contains("주간 회의 시간 조율");
        assertThat(message.content()).contains(NotificationActorText.PLACEHOLDER);
    }
}
