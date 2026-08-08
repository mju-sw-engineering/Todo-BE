package com.todo.domain.todo.entity;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkItemCheckInTest {

    @Test
    void 메모와_날짜로_체크인을_생성한다() {
        WorkItemCheckIn checkIn = WorkItemCheckIn.create(workItem(), user(), LocalDate.of(2026, 8, 7), "  오늘도 진행  ");

        assertThat(checkIn.getMemo()).isEqualTo("오늘도 진행");
        assertThat(checkIn.getCheckDate()).isEqualTo(LocalDate.of(2026, 8, 7));
    }

    @Test
    void 빈_메모는_거부한다() {
        assertThatThrownBy(() -> WorkItemCheckIn.create(workItem(), user(), LocalDate.now(), "   "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 백자를_넘는_메모는_거부한다() {
        String longMemo = "가".repeat(101);

        assertThatThrownBy(() -> WorkItemCheckIn.create(workItem(), user(), LocalDate.now(), longMemo))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private User user() {
        return User.create("user1", "encoded-password", "닉네임", null);
    }

    private TodoWorkItem workItem() {
        Team team = Team.create("팀", null, "invite-code");
        Todo todo = Todo.create(team, user(), "투두", null, LocalDateTime.now().plusDays(1));
        return TodoWorkItem.createDirect(todo, user());
    }
}
