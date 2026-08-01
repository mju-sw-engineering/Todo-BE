package com.todo.domain.user.repository;

import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TodoParticipantDetail;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoParticipantSummary;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴 시 공동 기록을 익명화하는 쿼리와, 익명 행이 조회에서 누락되지 않는지 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserWithdrawalRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private TodoParticipantRepository todoParticipantRepository;

    @Autowired
    private TeamChatMessageRepository teamChatMessageRepository;

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
        Todo mine = entityManager.persist(Todo.create(team, withdrawing, "내 투두", null, LocalDateTime.now().plusDays(1)));
        Todo others = entityManager.persist(Todo.create(team, staying, "남의 투두", null, LocalDateTime.now().plusDays(1)));
        entityManager.flush();

        int updated = todoRepository.clearCreatorByUserId(withdrawing.getId());
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(todoRepository.findById(mine.getId())).isPresent();
        assertThat(todoRepository.findById(mine.getId()).get().getCreator()).isNull();
        assertThat(todoRepository.findById(others.getId()).get().getCreator().getId()).isEqualTo(staying.getId());
    }

    @Test
    void 생성자가_익명인_투두도_목록_조회에서_누락되지_않는다() {
        setUpTeam();
        entityManager.persist(Todo.create(team, withdrawing, "익명 투두", null, LocalDateTime.now().plusDays(1)));
        entityManager.persist(Todo.create(team, staying, "일반 투두", null, LocalDateTime.now().plusDays(1)));
        entityManager.flush();
        todoRepository.clearCreatorByUserId(withdrawing.getId());
        entityManager.clear();

        List<Todo> todos = todoRepository.findByTeamIdWithCreator(team.getId());

        assertThat(todos).hasSize(2);
        assertThat(todos).anyMatch(todo -> todo.getCreator() == null);
    }

    @Test
    void 발신자가_익명인_채팅도_목록_조회에서_누락되지_않는다() {
        setUpTeam();
        entityManager.persist(TeamChatMessage.create(team, withdrawing, "떠나는 사람 메시지"));
        entityManager.persist(TeamChatMessage.create(team, staying, "남는 사람 메시지"));
        entityManager.flush();

        teamChatMessageRepository.clearSenderByUserId(withdrawing.getId());
        entityManager.flush();
        entityManager.clear();

        List<TeamChatMessage> messages = teamChatMessageRepository.findLatestMessages(team.getId(), PageRequest.of(0, 10));

        assertThat(messages).hasSize(2);
        assertThat(messages).anyMatch(message -> message.getSender() == null);
    }

    @Test
    void 완료_참가기록은_상태와_시각을_유지하고_사용자와_인증사진만_지운다() {
        setUpTeam();
        Todo todo = entityManager.persist(Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant finished = TodoParticipant.create(todo, withdrawing);
        finished.submit("proofs/original.png", "proofs/thumb.png");
        entityManager.persist(finished);
        entityManager.flush();
        LocalDateTime submittedAt = finished.getSubmittedAt();

        int updated = todoParticipantRepository.anonymizeFinishedByUserId(withdrawing.getId());
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        TodoParticipant reloaded = todoParticipantRepository.findById(finished.getId()).orElseThrow();
        assertThat(reloaded.getUser()).isNull();
        assertThat(reloaded.getProofImageKey()).isNull();
        assertThat(reloaded.getProofThumbnailKey()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(ParticipantStatus.SUCCESS);
        assertThat(reloaded.getSubmittedAt()).isEqualTo(submittedAt);
    }

    @Test
    void 진행중_참가기록만_삭제하고_완료_기록은_남긴다() {
        setUpTeam();
        Todo finishedTodo = entityManager.persist(Todo.create(team, staying, "완료", null, LocalDateTime.now().plusDays(1)));
        Todo activeTodo = entityManager.persist(Todo.create(team, staying, "진행", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant finished = TodoParticipant.create(finishedTodo, withdrawing);
        finished.markAsSuccess();
        entityManager.persist(finished);
        TodoParticipant inProgress = entityManager.persist(TodoParticipant.create(activeTodo, withdrawing));
        entityManager.flush();

        int deleted = todoParticipantRepository.deleteInProgressByUserId(withdrawing.getId());
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(todoParticipantRepository.findById(inProgress.getId())).isEmpty();
        assertThat(todoParticipantRepository.findById(finished.getId())).isPresent();
    }

    @Test
    void 익명_참가기록도_요약과_상세_조회에_포함된다() {
        setUpTeam();
        Todo todo = entityManager.persist(Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant anonymized = TodoParticipant.create(todo, withdrawing);
        anonymized.markAsSuccess();
        entityManager.persist(anonymized);
        entityManager.persist(TodoParticipant.create(todo, staying));
        entityManager.flush();
        todoParticipantRepository.anonymizeFinishedByUserId(withdrawing.getId());
        entityManager.clear();

        List<TodoParticipantSummary> summaries = todoParticipantRepository.findSummaryByTodoIdIn(List.of(todo.getId()));
        List<TodoParticipantDetail> details = todoParticipantRepository.findDetailByTodoId(todo.getId());

        // INNER JOIN이면 익명 행이 통째로 사라진다.
        assertThat(summaries).hasSize(2);
        assertThat(summaries).anyMatch(summary -> summary.getUserId() == null && summary.getNickname() == null);
        assertThat(details).hasSize(2);
        assertThat(details).anyMatch(detail -> detail.getUserId() == null);
    }

    @Test
    void 참가자가_모두_빠진_투두는_FAIL로_확정된다() {
        setUpTeam();
        Todo todo = entityManager.persist(Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        entityManager.persist(TodoParticipant.create(todo, withdrawing));
        entityManager.flush();
        todoParticipantRepository.deleteInProgressByUserId(withdrawing.getId());

        todoRepository.markAsFailWhenNoParticipantsRemain(List.of(todo.getId()));
        entityManager.clear();

        assertThat(todoRepository.findById(todo.getId()).orElseThrow().getStatus()).isEqualTo(TodoStatus.FAIL);
    }

    @Test
    void 잔여_참가자가_전원_성공이면_SUCCESS로_확정된다() {
        setUpTeam();
        Todo todo = entityManager.persist(Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant succeeded = TodoParticipant.create(todo, staying);
        succeeded.markAsSuccess();
        entityManager.persist(succeeded);
        entityManager.persist(TodoParticipant.create(todo, withdrawing));
        entityManager.flush();
        todoParticipantRepository.deleteInProgressByUserId(withdrawing.getId());

        todoRepository.markAsSuccessWhenRemainingAllSucceeded(List.of(todo.getId()));
        entityManager.clear();

        assertThat(todoRepository.findById(todo.getId()).orElseThrow().getStatus()).isEqualTo(TodoStatus.SUCCESS);
    }

    @Test
    void 잔여_참가자가_미완료면_진행중을_유지한다() {
        setUpTeam();
        Todo todo = entityManager.persist(Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        entityManager.persist(TodoParticipant.create(todo, staying));
        entityManager.persist(TodoParticipant.create(todo, withdrawing));
        entityManager.flush();
        todoParticipantRepository.deleteInProgressByUserId(withdrawing.getId());

        todoRepository.markAsFailWhenNoParticipantsRemain(List.of(todo.getId()));
        todoRepository.markAsSuccessWhenRemainingAllSucceeded(List.of(todo.getId()));
        entityManager.clear();

        assertThat(todoRepository.findById(todo.getId()).orElseThrow().getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
    }

    @Test
    void 참가자가_0명인_투두는_전원성공_조건으로_SUCCESS_처리되지_않는다() {
        setUpTeam();
        Todo todo = entityManager.persist(Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        entityManager.persist(TodoParticipant.create(todo, withdrawing));
        entityManager.flush();
        todoParticipantRepository.deleteInProgressByUserId(withdrawing.getId());

        // 0 == 0으로 "미완료 없음"이 성립해 성공 처리되면 안 된다.
        todoRepository.markAsSuccessWhenRemainingAllSucceeded(List.of(todo.getId()));
        entityManager.clear();

        assertThat(todoRepository.findById(todo.getId()).orElseThrow().getStatus()).isNotEqualTo(TodoStatus.SUCCESS);
    }

    @Test
    void 이미_확정된_투두는_재평가로_되돌리지_않는다() {
        setUpTeam();
        Todo todo = entityManager.persist(Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        todo.markAsFail();
        TodoParticipant succeeded = TodoParticipant.create(todo, staying);
        succeeded.markAsSuccess();
        entityManager.persist(succeeded);
        entityManager.flush();

        // 마감 스케줄러가 FAIL로 확정한 투두를 탈퇴 재평가가 SUCCESS로 되돌리면 안 된다.
        todoRepository.markAsSuccessWhenRemainingAllSucceeded(List.of(todo.getId()));
        entityManager.clear();

        assertThat(todoRepository.findById(todo.getId()).orElseThrow().getStatus()).isEqualTo(TodoStatus.FAIL);
    }
}
