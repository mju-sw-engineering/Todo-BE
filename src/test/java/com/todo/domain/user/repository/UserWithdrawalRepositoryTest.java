package com.todo.domain.user.repository;

import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemDetail;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.TodoWorkItemSummary;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 회원 탈퇴 시 익명화 쿼리와 익명 WorkItem이 조회에서 누락되지 않는지 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserWithdrawalRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private TodoRepository todoRepository;
    @Autowired private TodoWorkItemRepository todoWorkItemRepository;
    @Autowired private TeamChatMessageRepository teamChatMessageRepository;

    private Team team;
    private User withdrawing;
    private User staying;

    private void setUpTeam() {
        team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        withdrawing = entityManager.persist(User.create("leaving", "pw", "떠나는사람", null));
        staying = entityManager.persist(User.create("staying", "pw", "남는사람", null));
    }

    @Test
    void 생성자를_익명화해도_투두는_남고_대상_사용자만_바뀐다() {
        setUpTeam();
        Todo mine = entityManager.persist(todo(withdrawing, "내 투두", TodoMode.DIRECT));
        Todo others = entityManager.persist(todo(staying, "남의 투두", TodoMode.DIRECT));
        entityManager.flush();

        int updated = todoRepository.clearCreatorByUserId(withdrawing.getId());
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(todoRepository.findById(mine.getId())).hasValueSatisfying(todo -> assertThat(todo.getCreator()).isNull());
        assertThat(todoRepository.findById(others.getId())).hasValueSatisfying(
                todo -> assertThat(todo.getCreator().getId()).isEqualTo(staying.getId()));
    }

    @Test
    void 생성자와_발신자가_익명이어도_투두와_채팅_목록에서_누락되지_않는다() {
        setUpTeam();
        entityManager.persist(todo(withdrawing, "익명 투두", TodoMode.DIRECT));
        entityManager.persist(todo(staying, "일반 투두", TodoMode.DIRECT));
        entityManager.persist(TeamChatMessage.create(team, withdrawing, "떠나는 사람 메시지"));
        entityManager.persist(TeamChatMessage.create(team, staying, "남는 사람 메시지"));
        entityManager.flush();
        todoRepository.clearCreatorByUserId(withdrawing.getId());
        teamChatMessageRepository.clearSenderByUserId(withdrawing.getId());
        entityManager.clear();

        assertThat(todoRepository.findByTeamIdWithCreator(team.getId()))
                .hasSize(2)
                .anyMatch(todo -> todo.getCreator() == null);
        assertThat(teamChatMessageRepository.findLatestMessages(team.getId(), PageRequest.of(0, 10)))
                .hasSize(2)
                .anyMatch(message -> message.getSender() == null);
    }

    @Test
    void 완료_WorkItem은_상태와_제출시각을_보존하고_담당자와_사진키만_익명화한다() {
        setUpTeam();
        Todo todo = entityManager.persist(todo(staying, "투두", TodoMode.DIRECT));
        TodoWorkItem finished = TodoWorkItem.createDirect(todo, withdrawing);
        finished.submit("proofs/original.png", "proofs/thumb.png", "image/png", null);
        entityManager.persist(finished);
        entityManager.flush();
        LocalDateTime submittedAt = finished.getSubmittedAt();

        int updated = todoWorkItemRepository.anonymizeFinishedByAssigneeId(withdrawing.getId());
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        TodoWorkItem reloaded = todoWorkItemRepository.findById(finished.getId()).orElseThrow();
        assertThat(reloaded.getAssignee()).isNull();
        assertThat(reloaded.getProofImageKey()).isNull();
        assertThat(reloaded.getProofThumbnailKey()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(WorkItemStatus.SUCCESS);
        assertThat(reloaded.getSubmittedAt()).isCloseTo(submittedAt, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void 익명화된_TASK_완료기록도_요약과_상세조회에_포함된다() {
        setUpTeam();
        Todo todo = entityManager.persist(todo(staying, "발표", TodoMode.TASK));
        TodoWorkItem completedTask = TodoWorkItem.createTask(
                todo, withdrawing, "자료 만들기", null, LocalDateTime.now().plusHours(1), 0);
        completedTask.markAsSuccess();
        entityManager.persist(completedTask);
        entityManager.persist(TodoWorkItem.createTask(
                todo, staying, "발표 연습", null, LocalDateTime.now().plusHours(2), 1));
        entityManager.flush();
        todoWorkItemRepository.anonymizeFinishedByAssigneeId(withdrawing.getId());
        entityManager.clear();

        List<TodoWorkItemSummary> summaries = todoWorkItemRepository.findSummaryByTodoIdIn(List.of(todo.getId()));
        List<TodoWorkItemDetail> details = todoWorkItemRepository.findDetailByTodoId(todo.getId());

        assertThat(summaries).hasSize(2)
                .anyMatch(summary -> summary.getAssigneeId() == null && summary.getType() == WorkItemType.TASK);
        assertThat(details).hasSize(2)
                .anyMatch(detail -> detail.getAssigneeId() == null && detail.getStatus() == WorkItemStatus.SUCCESS);
    }

    @Test
    void 진행중_DIRECT_행만_일괄삭제_대상이고_TASK_행은_미배정으로_보존할_수_있다() {
        setUpTeam();
        Todo directTodo = entityManager.persist(todo(staying, "공동 인증", TodoMode.DIRECT));
        Todo taskTodo = entityManager.persist(todo(staying, "발표", TodoMode.TASK));
        TodoWorkItem direct = entityManager.persist(TodoWorkItem.createDirect(directTodo, withdrawing));
        TodoWorkItem task = entityManager.persist(TodoWorkItem.createTask(
                taskTodo, withdrawing, "PPT 만들기", null, LocalDateTime.now().plusHours(1), 0));
        entityManager.flush();

        int deleted = todoWorkItemRepository.deleteInProgressDirectByAssigneeId(withdrawing.getId());
        TodoWorkItem managedTask = todoWorkItemRepository.findById(task.getId()).orElseThrow();
        managedTask.unassign();
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(todoWorkItemRepository.findById(direct.getId())).isEmpty();
        assertThat(todoWorkItemRepository.findById(task.getId())).hasValueSatisfying(workItem -> {
            assertThat(workItem.getAssignee()).isNull();
            assertThat(workItem.getStatus()).isEqualTo(WorkItemStatus.IN_PROGRESS);
        });
    }

    private Todo todo(User creator, String title, TodoMode mode) {
        return Todo.create(team, creator, title, null, LocalDateTime.now().plusDays(1), mode);
    }
}
