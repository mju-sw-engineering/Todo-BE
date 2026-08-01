package com.todo.domain.chat.dto.response;

import com.todo.domain.chat.entity.ChatMessage;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageResponseTest {

    /**
     * 응답 매핑은 todo를 참조하지 않으므로 발신자만 바꿔가며 검증한다.
     */
    private ChatMessage messageFrom(User sender) {
        ChatMessage message = ChatMessage.create(null, sender, "안녕하세요");
        ReflectionTestUtils.setField(message, "id", 1L);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 8, 1, 12, 0));
        return message;
    }

    @Test
    void 발신자가_있으면_닉네임과_프로필을_그대로_내려준다() {
        User sender = User.create("user1", "pw", "보낸사람", "profiles/user1.png");
        ReflectionTestUtils.setField(sender, "id", 7L);

        ChatMessageResponse response = ChatMessageResponse.from(messageFrom(sender));

        assertThat(response.senderId()).isEqualTo(7L);
        assertThat(response.senderNickname()).isEqualTo("보낸사람");
        assertThat(response.senderProfileImageUrl()).isEqualTo("profiles/user1.png");
        assertThat(response.content()).isEqualTo("안녕하세요");
    }

    @Test
    void 발신자가_탈퇴하면_메시지는_남기고_작성자만_익명으로_내려준다() {
        ChatMessageResponse response = ChatMessageResponse.from(messageFrom(null));

        assertThat(response.senderNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.senderId()).isNull();
        assertThat(response.senderProfileImageUrl()).isNull();
        assertThat(response.content()).isEqualTo("안녕하세요");
    }
}
