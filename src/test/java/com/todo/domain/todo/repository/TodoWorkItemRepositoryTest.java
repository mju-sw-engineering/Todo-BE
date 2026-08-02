package com.todo.domain.todo.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TodoWorkItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoWorkItemRepository todoWorkItemRepository;

    @Test
    void 유효_마감이_지난_진행중_WorkItem만_FAIL로_바꾼다() {
        LocalDateTime now = LocalDateTime.now();
        Team team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        User memberA = entityManager.persist(User.create("loginA", "pw", "닉A", null));
        User memberB = entityManager.persist(User.create("loginB", "pw", "닉B", null));

        Todo expiredDirectTodo = entityManager.persist(Todo.create(
                team, memberA, "만료 DIRECT", null, now.minusHours(1), TodoMode.DIRECT));
        Todo activeTaskTodo = entityManager.persist(Todo.create(
                team, memberA, "진행 TASK", null, now.plusHours(2), TodoMode.TASK));
        Todo expiredTaskTodo = entityManager.persist(Todo.create(
                team, memberA, "Task 만료", null, now.plusHours(2), TodoMode.TASK));

        TodoWorkItem expiredDirect = entityManager.persist(TodoWorkItem.createDirect(expiredDirectTodo, memberA));
        TodoWorkItem activeTask = entityManager.persist(TodoWorkItem.createTask(
                activeTaskTodo, memberA, "진행", null, now.plusHours(1), 0));
        TodoWorkItem expiredTask = entityManager.persist(TodoWorkItem.createTask(
                expiredTaskTodo, memberB, "만료", null, now.minusHours(1), 0));
        entityManager.flush();

        int updated = todoWorkItemRepository.markOverdueAsFail(now);
        entityManager.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(entityManager.find(TodoWorkItem.class, expiredDirect.getId()).getStatus())
                .isEqualTo(WorkItemStatus.FAIL);
        assertThat(entityManager.find(TodoWorkItem.class, expiredTask.getId()).getStatus())
                .isEqualTo(WorkItemStatus.FAIL);
        assertThat(entityManager.find(TodoWorkItem.class, activeTask.getId()).getStatus())
                .isEqualTo(WorkItemStatus.IN_PROGRESS);
    }

    @Test
    void 팀삭제용_인증사진_키는_대상_Todo의_null이_아닌_키만_조회한다() {
        Team team = entityManager.persist(Team.create("팀", null, "INVITE2"));
        User member = entityManager.persist(User.create("member", "pw", "닉", null));
        User memberWithoutFiles = entityManager.persist(User.create("member2", "pw", "닉2", null));
        Todo target = entityManager.persist(Todo.create(
                team, member, "대상", null, LocalDateTime.now().plusHours(1), TodoMode.DIRECT));
        Todo other = entityManager.persist(Todo.create(
                team, member, "다른 투두", null, LocalDateTime.now().plusHours(1), TodoMode.DIRECT));
        TodoWorkItem withFiles = TodoWorkItem.createDirect(target, member);
        withFiles.submit("proofs/original.png", "proofs/thumb.jpg");
        entityManager.persist(withFiles);
        entityManager.persist(TodoWorkItem.createDirect(target, memberWithoutFiles));
        TodoWorkItem otherFiles = TodoWorkItem.createDirect(other, member);
        otherFiles.submit("proofs/other.png", "proofs/other-thumb.jpg");
        entityManager.persist(otherFiles);
        entityManager.flush();

        assertThat(todoWorkItemRepository.findProofImageKeysByTodoIdIn(List.of(target.getId())))
                .containsExactly("proofs/original.png");
        assertThat(todoWorkItemRepository.findProofThumbnailKeysByTodoIdIn(List.of(target.getId())))
                .containsExactly("proofs/thumb.jpg");
    }
}
