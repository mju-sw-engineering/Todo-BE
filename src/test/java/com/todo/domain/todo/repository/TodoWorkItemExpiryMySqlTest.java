package com.todo.domain.todo.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.support.MySqlTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마감 만료 벌크 UPDATE의 MySQL 회귀 테스트.
 *
 * <p>{@code markOverdueAsFail}이 {@code wi.todo.deadline} 암시적 조인을 쓰면 Hibernate가
 * {@code UPDATE ... JOIN}을 만들며 SET의 status가 테이블 한정 없이 생성돼, 운영 MySQL에서
 * "Column 'status' in field list is ambiguous"로 스케줄러가 매 분 실패했다.
 * H2(MODE=MySQL)는 이 쿼리를 통과시키므로 실제 MySQL로 실행해야 잡힌다.
 */
@SpringBootTest
@Transactional
class TodoWorkItemExpiryMySqlTest extends MySqlTestSupport {

    @Autowired
    private TodoWorkItemRepository todoWorkItemRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 마감_지난_작업을_실패로_바꾸는_벌크_UPDATE가_MySQL에서_실행된다() {
        User user = userRepository.save(User.create("expiry-tester", "encoded", "만료테스터", null));
        Team team = teamRepository.save(Team.create("만료팀", null, "EXPIRY01"));
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 12, 0);

        // 작업 자체 마감은 없고 투두 마감이 지난 케이스 — COALESCE가 투두 마감으로 판정해야 한다
        Todo overdue = Todo.create(team, user, "지난 투두", null, now.minusHours(1));
        entityManager.persist(overdue);
        TodoWorkItem overdueItem = TodoWorkItem.createDirect(overdue, user);
        entityManager.persist(overdueItem);

        // 마감이 남은 케이스는 그대로여야 한다
        Todo future = Todo.create(team, user, "남은 투두", null, now.plusHours(1));
        entityManager.persist(future);
        TodoWorkItem futureItem = TodoWorkItem.createDirect(future, user);
        entityManager.persist(futureItem);
        entityManager.flush();

        int updated = todoWorkItemRepository.markOverdueAsFail(now);

        assertThat(updated).isEqualTo(1);
        entityManager.clear();
        assertThat(todoWorkItemRepository.findById(overdueItem.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkItemStatus.FAIL);
        assertThat(todoWorkItemRepository.findById(futureItem.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkItemStatus.IN_PROGRESS);
    }
}
