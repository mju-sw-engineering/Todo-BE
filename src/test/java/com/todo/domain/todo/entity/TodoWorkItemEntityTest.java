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
    void TASK_WorkItem은_개별_마감이_없으면_투두_마감을_유효_마감으로_사용한다() {
        Todo todo = todo(LocalDateTime.of(2026, 8, 2, 18, 0));

        TodoWorkItem workItem = TodoWorkItem.createTask(
                todo,
                user(),
                "발표 자료 만들기",
                null,
                null,
                0
        );

        assertThat(workItem.getDeadline()).isNull();
        assertThat(workItem.getEffectiveDeadline()).isEqualTo(todo.getDeadline());
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

        workItem.submit("proof-key", "thumb-key", "image/png", null);

        assertThat(workItem.getStatus()).isEqualTo(WorkItemStatus.SUCCESS);
        assertThat(workItem.getProofThumbnailKey()).isEqualTo("thumb-key");
        assertThatThrownBy(() -> workItem.submit("another-proof-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 제출되었거나 완료된 투두입니다.");
    }

    @Test
    void 제출_파일의_종류는_contentType으로_판단한다() {
        TodoWorkItem image = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());
        image.submit("proof.jpg", "thumb.jpg", "image/jpeg", "인증사진.jpg");
        assertThat(image.getProofKind()).isEqualTo(ProofKind.IMAGE);

        TodoWorkItem document = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());
        document.submit("proof.pdf", null, "application/pdf", "발표자료.pdf");
        assertThat(document.getProofKind()).isEqualTo(ProofKind.DOCUMENT);
        assertThat(document.getProofFileName()).isEqualTo("발표자료.pdf");
    }

    @Test
    void 확장자가_이미지여도_contentType이_문서면_문서로_본다() {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());

        // 확장자는 클라이언트가 붙인 파일명에서 오지만 contentType은 업로드 시점에 강제된 값이다.
        workItem.submit("proof.jpg", null, "application/pdf", "위장.jpg");

        assertThat(workItem.getProofKind()).isEqualTo(ProofKind.DOCUMENT);
    }

    @Test
    void contentType이_없는_기존_제출은_종류를_단정하지_않는다() {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());

        workItem.submit("proof-key");

        assertThat(workItem.getProofKind()).isNull();
    }

    @Test
    void 파일명은_경로_구분자를_제거하고_길이를_제한한다() {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());

        workItem.submit("proof.pdf", null, "application/pdf", "../../etc/passwd.pdf");

        assertThat(workItem.getProofFileName()).isEqualTo("....etcpasswd.pdf");

        TodoWorkItem longName = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());
        longName.submit("proof.pdf", null, "application/pdf", "가".repeat(300) + ".pdf");

        assertThat(longName.getProofFileName()).hasSize(255);
    }

    @Test
    void 빈_파일명은_null로_저장한다() {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo(LocalDateTime.now().plusHours(1)), user());

        workItem.submit("proof.pdf", null, "application/pdf", "   ");

        assertThat(workItem.getProofFileName()).isNull();
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
