package com.todo.domain.todo.entity;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoEntityTest {

    @Test
    void 진행중_투두만_실패로_변경된다() {
        Todo todo = todo();

        todo.markAsFail();
        todo.markAsSuccess();
        todo.markAsFail();

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
    }

    @Test
    void 참가자는_인증사진과_썸네일을_제출한다() {
        TodoParticipant participant = TodoParticipant.create(todo(), user());

        participant.submit("proof-key", "thumb-key");

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.SUCCESS);
        assertThat(participant.getProofImageKey()).isEqualTo("proof-key");
        assertThat(participant.getProofThumbnailKey()).isEqualTo("thumb-key");
        assertThat(participant.getSubmittedAt()).isNotNull();
    }

    @Test
    void 이미_완료된_참가자는_다시_제출할_수_없다() {
        TodoParticipant participant = TodoParticipant.create(todo(), user());
        participant.markAsSuccess();

        assertThatThrownBy(() -> participant.submit("proof-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 제출되었거나 완료된 투두입니다.");
    }

    private Todo todo() {
        return Todo.create(
                Team.create("팀", null, "ABCDEFGH"),
                user(),
                "투두",
                "설명",
                LocalDateTime.of(2026, 6, 4, 12, 0)
        );
    }

    private User user() {
        return User.create("user1", "encoded", "닉네임", null);
    }
}
