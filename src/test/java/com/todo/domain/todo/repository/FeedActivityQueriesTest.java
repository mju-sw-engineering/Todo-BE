package com.todo.domain.todo.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemCheckIn;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 피드 집계가 쓰는 활동 조회 쿼리의 실제 결과를 검증한다.
 * 팀 경계, 기간 필터, 탈퇴 익명화(null 창작자/담당자) 제외가 핵심이다.
 */
@DataJpaTest
@ActiveProfiles("test")
class FeedActivityQueriesTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 7, 10, 0);
    private static final LocalDateTime FROM = BASE.minusDays(30);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private TodoWorkItemRepository todoWorkItemRepository;

    @Autowired
    private WorkItemCheckInRepository workItemCheckInRepository;

    private Team myTeam;
    private Team otherTeam;
    private User me;
    private User teammate;

    @BeforeEach
    void setUp() {
        myTeam = entityManager.persist(Team.create("우리팀", null, "INVITE-A"));
        otherTeam = entityManager.persist(Team.create("남의팀", null, "INVITE-B"));
        me = entityManager.persist(User.create("me", "pw", "나", null));
        teammate = entityManager.persist(User.create("mate", "pw", "동료", null));
    }

    @Test
    void 팀_생성_활동은_팀과_기간으로_거르고_익명화된_창작자는_제외한다() {
        persistTodoAt(myTeam, me, BASE.minusDays(1));
        persistTodoAt(myTeam, teammate, BASE.minusDays(40)); // 기간 밖
        persistTodoAt(otherTeam, me, BASE.minusDays(1)); // 다른 팀
        Todo anonymized = persistTodoAt(myTeam, me, BASE.minusDays(2));
        ReflectionTestUtils.setField(anonymized, "creator", null); // 탈퇴 익명화
        entityManager.flush();

        List<UserActivityRecord> result = todoRepository.findCreationActivityByTeamId(myTeam.getId(), FROM);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(me.getId());
    }

    @Test
    void 내_생성_활동은_내가_만든_투두만_돌려준다() {
        Todo mine = persistTodoAt(myTeam, me, BASE.minusDays(1));
        persistTodoAt(myTeam, teammate, BASE.minusDays(1));
        entityManager.flush();

        List<UserActivityRecord> result = todoRepository.findCreationActivityByCreatorId(me.getId(), FROM);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTodoId()).isEqualTo(mine.getId());
    }

    @Test
    void 팀_제출_활동은_미제출과_익명화된_담당자를_제외한다() {
        Todo todo = persistTodoAt(myTeam, me, BASE.minusDays(5));
        persistSubmittedItem(todo, me, BASE.minusDays(1));
        entityManager.persist(TodoWorkItem.createDirect(todo, teammate)); // 미제출
        TodoWorkItem anonymized = persistSubmittedItem(todo, teammate, BASE.minusDays(2));
        anonymized.unassign(); // 탈퇴 익명화
        Todo otherTodo = persistTodoAt(otherTeam, me, BASE.minusDays(5));
        persistSubmittedItem(otherTodo, me, BASE.minusDays(1)); // 다른 팀
        entityManager.flush();

        List<UserActivityRecord> result =
                todoWorkItemRepository.findSubmissionActivityByTeamId(myTeam.getId(), FROM);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(me.getId());
    }

    @Test
    void 내_제출_활동은_담당자_기준으로_거른다() {
        Todo todo = persistTodoAt(myTeam, me, BASE.minusDays(5));
        persistSubmittedItem(todo, me, BASE.minusDays(1));
        persistSubmittedItem(todo, teammate, BASE.minusDays(1));
        entityManager.flush();

        List<UserActivityRecord> result =
                todoWorkItemRepository.findSubmissionActivityByAssigneeId(me.getId(), FROM);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTodoId()).isEqualTo(todo.getId());
    }

    @Test
    void 팀_체크인_활동은_팀과_기간으로_거른다() {
        Todo todo = persistTodoAt(myTeam, me, BASE.minusDays(5));
        TodoWorkItem item = entityManager.persist(TodoWorkItem.createDirect(todo, me));
        entityManager.persist(WorkItemCheckIn.create(item, me, BASE.minusDays(1).toLocalDate(), "진행"));
        entityManager.persist(WorkItemCheckIn.create(item, me, BASE.minusDays(40).toLocalDate(), "오래됨"));
        Todo otherTodo = persistTodoAt(otherTeam, teammate, BASE.minusDays(5));
        TodoWorkItem otherItem = entityManager.persist(TodoWorkItem.createDirect(otherTodo, teammate));
        entityManager.persist(WorkItemCheckIn.create(otherItem, teammate, BASE.minusDays(1).toLocalDate(), "남의팀"));
        entityManager.flush();

        List<CheckInActivityRecord> result =
                workItemCheckInRepository.findActivityByTeamId(myTeam.getId(), FROM.toLocalDate());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(me.getId());
        assertThat(result.get(0).getTodoId()).isEqualTo(todo.getId());
    }

    @Test
    void 내_체크인_활동은_사용자_기준으로_거른다() {
        Todo todo = persistTodoAt(myTeam, me, BASE.minusDays(5));
        TodoWorkItem mine = entityManager.persist(TodoWorkItem.createDirect(todo, me));
        TodoWorkItem mates = entityManager.persist(TodoWorkItem.createDirect(todo, teammate));
        entityManager.persist(WorkItemCheckIn.create(mine, me, BASE.minusDays(1).toLocalDate(), "내 것"));
        entityManager.persist(WorkItemCheckIn.create(mates, teammate, BASE.minusDays(1).toLocalDate(), "동료 것"));
        entityManager.flush();

        List<CheckInActivityRecord> result =
                workItemCheckInRepository.findActivityByUserId(me.getId(), FROM.toLocalDate());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(me.getId());
    }

    @Test
    void 체크인_목록은_최신_날짜부터_돌려준다() {
        Todo todo = persistTodoAt(myTeam, me, BASE.minusDays(5));
        TodoWorkItem item = entityManager.persist(TodoWorkItem.createDirect(todo, me));
        entityManager.persist(WorkItemCheckIn.create(item, me, LocalDate.of(2026, 8, 5), "첫날"));
        entityManager.persist(WorkItemCheckIn.create(item, me, LocalDate.of(2026, 8, 7), "셋째날"));
        entityManager.persist(WorkItemCheckIn.create(item, me, LocalDate.of(2026, 8, 6), "둘째날"));
        entityManager.flush();

        List<WorkItemCheckIn> result = workItemCheckInRepository.findByWorkItemIdWithUser(item.getId());

        assertThat(result).extracting(WorkItemCheckIn::getMemo)
                .containsExactly("셋째날", "둘째날", "첫날");
    }

    @Test
    void 같은_날_같은_항목의_중복_체크인은_DB_제약이_막는다() {
        Todo todo = persistTodoAt(myTeam, me, BASE.minusDays(5));
        TodoWorkItem item = entityManager.persist(TodoWorkItem.createDirect(todo, me));
        LocalDate today = LocalDate.of(2026, 8, 7);
        entityManager.persist(WorkItemCheckIn.create(item, me, today, "첫 번째"));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(WorkItemCheckIn.create(item, me, today, "두 번째"));
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void 오늘_체크인_존재_여부를_확인한다() {
        Todo todo = persistTodoAt(myTeam, me, BASE.minusDays(5));
        TodoWorkItem item = entityManager.persist(TodoWorkItem.createDirect(todo, me));
        LocalDate today = LocalDate.of(2026, 8, 7);
        entityManager.persist(WorkItemCheckIn.create(item, me, today, "진행"));
        entityManager.flush();

        assertThat(workItemCheckInRepository
                .existsByWorkItemIdAndUserIdAndCheckDate(item.getId(), me.getId(), today)).isTrue();
        assertThat(workItemCheckInRepository
                .existsByWorkItemIdAndUserIdAndCheckDate(item.getId(), me.getId(), today.plusDays(1))).isFalse();
    }

    private Todo persistTodoAt(Team team, User creator, LocalDateTime createdAt) {
        Todo todo = Todo.create(team, creator, "투두", null, BASE.plusDays(30));
        entityManager.persist(todo);
        entityManager.flush();
        // createdAt은 updatable=false라 dirty checking으로는 못 바꾼다. 기간 필터 검증용으로 직접 덮어쓴다.
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE todos SET created_at = :ts WHERE id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", todo.getId())
                .executeUpdate();
        return todo;
    }

    private TodoWorkItem persistSubmittedItem(Todo todo, User assignee, LocalDateTime submittedAt) {
        TodoWorkItem item = TodoWorkItem.createDirect(todo, assignee);
        item.submit("proof-" + System.nanoTime());
        ReflectionTestUtils.setField(item, "submittedAt", submittedAt);
        return entityManager.persist(item);
    }
}
