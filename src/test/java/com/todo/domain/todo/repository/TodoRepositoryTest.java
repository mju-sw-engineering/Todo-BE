package com.todo.domain.todo.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoStatus;
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
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TodoRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoRepository todoRepository;

    @Test
    void 팀현황_투두_집계는_상태별로_세고_다른_팀_데이터는_섞이지_않는다() {
        LocalDateTime now = LocalDateTime.now();
        Team team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        Team otherTeam = entityManager.persist(Team.create("다른팀", null, "INVITE2"));
        User member = entityManager.persist(User.create("login1", "pw", "닉1", null));

        entityManager.persist(Todo.create(team, member, "진행1", null, now.plusDays(1), TodoMode.DIRECT));
        entityManager.persist(Todo.create(team, member, "진행2", null, now.plusDays(1), TodoMode.DIRECT));
        Todo success = entityManager.persist(Todo.create(team, member, "성공", null, now.plusDays(1), TodoMode.DIRECT));
        success.markAsSuccess();
        Todo otherTeamTodo = entityManager.persist(Todo.create(otherTeam, member, "다른팀", null, now.plusDays(1), TodoMode.DIRECT));
        otherTeamTodo.markAsSuccess();
        entityManager.flush();

        List<TodoStatusCount> result = todoRepository.countByTeamIdGroupByStatus(team.getId());

        assertThat(result)
                .extracting(TodoStatusCount::getStatus, TodoStatusCount::getCount)
                .containsExactlyInAnyOrder(
                        tuple(TodoStatus.IN_PROGRESS, 2L),
                        tuple(TodoStatus.SUCCESS, 1L)
                );
    }
}
