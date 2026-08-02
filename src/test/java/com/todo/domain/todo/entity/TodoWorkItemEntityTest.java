package com.todo.domain.todo.entity;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoWorkItemEntityTest {

    @Test
    void DIRECT_WorkItem은_투두_마감을_유효_마감으로_사용한다() {
        Todo todo = todo(LocalDateTime.of(2026, 8, 2, 18, 0));

        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, user());

        assertThat(workItem.getType()).isEqualTo(WorkItemType.DIRECT);
        assertThat(workItem.getDeadline()).isNull();
        assertThat(workItem.getEffectiveDeadline()).isEqualTo(todo.getDeadline());
    }

    @Test
    void TASK_WorkItem은_제목과_개별_마감을_가진다() {
        Todo todo = todo(LocalDateTime.of(2026, 8, 2, 18, 0));

        TodoWorkItem workItem = TodoWorkItem.createTask(
                todo,
                user(),
                "발표 자료 만들기",
                "1~10페이지",
                LocalDateTime.of(2026, 8, 2, 17, 0),
                1
        );

        assertThat(workItem.getType()).isEqualTo(WorkItemType.TASK);
        assertThat(workItem.getTaskTitle()).isEqualTo("발표 자료 만들기");
        assertThat(workItem.getEffectiveDeadline()).isEqualTo(LocalDateTime.of(2026, 8, 2, 17, 0));
    }

    @Test
    void TASK_마감은_부모_Todo_마감을_넘을_수_없다() {
        Todo todo = todo(LocalDateTime.of(2026, 8, 2, 18, 0));

        assertThatThrownBy(() -> TodoWorkItem.createTask(
                todo,
                user(),
                "발표 자료 만들기",
                null,
                LocalDateTime.of(2026, 8, 2, 18, 1),
                0
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task 마감일은 Todo 마감일을 넘을 수 없습니다.");
    }

    @Test
    void WorkItem은_제출_후_중복_제출할_수_없다() {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());

        workItem.submit("proof-key", "thumb-key");

        assertThat(workItem.getStatus()).isEqualTo(WorkItemStatus.SUCCESS);
        assertThat(workItem.getProofThumbnailKey()).isEqualTo("thumb-key");
        assertThatThrownBy(() -> workItem.submit("another-proof-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 제출되었거나 완료된 투두입니다.");
    }

    @Test
    void 실패한_Todo는_성공으로_되돌아가지_않는다() {
        Todo todo = todo(LocalDateTime.now().plusHours(1));

        todo.markAsFail();

        assertThat(todo.markAsSuccess()).isFalse();
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.FAIL);
    }

    private Todo todo(LocalDateTime deadline) {
        return Todo.create(
                Team.create("팀", null, "ABCDEFGH"),
                user(),
                "투두",
                "설명",
                deadline,
                TodoMode.TASK
        );
    }

    private User user() {
        return User.create("user1", "encoded", "닉네임", null);
    }
}
