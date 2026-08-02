package com.todo.domain.user.service;

import com.todo.domain.auth.dto.request.ReauthRequest;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.ReauthTokenRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.chat.entity.TeamChatReadStatus;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.dto.response.NotificationResponse;
import com.todo.domain.notification.entity.Notification;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationActorText;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.file.repository.FileDeletionOutboxRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 엔티티 그래프와 FK 제약이 살아 있는 탈퇴 전체 흐름 검증. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserWithdrawalIntegrationTest {

    private static final String RAW_PASSWORD = "rawPassword123!";

    @Autowired private UserService userService;
    @Autowired private EntityManager entityManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private TodoWorkItemRepository todoWorkItemRepository;
    @Autowired private TodoReactionRepository todoReactionRepository;
    @Autowired private TeamChatMessageRepository teamChatMessageRepository;
    @Autowired private TeamChatReadStatusRepository teamChatReadStatusRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserConsentRepository userConsentRepository;
    @Autowired private ReauthService reauthService;
    @Autowired private ReauthTokenRepository reauthTokenRepository;
    @Autowired private FileDeletionOutboxRepository fileDeletionOutboxRepository;

    private User withdrawing;
    private User staying;
    private Team team;

    @BeforeEach
    void setUp() {
        withdrawing = userRepository.save(User.create(
                "leaving", passwordEncoder.encode(RAW_PASSWORD), "떠나는사람", "profiles/leaving.png"));
        withdrawing.assignEmail("leaving@example.com");
        staying = userRepository.save(User.create(
                "staying", passwordEncoder.encode(RAW_PASSWORD), "남는사람", null));
        team = teamRepository.save(Team.create("팀", null, "INVITE01"));
        teamMemberRepository.save(TeamMember.create(team, staying, TeamMemberRole.LEADER));
        teamMemberRepository.save(TeamMember.create(team, withdrawing, TeamMemberRole.MEMBER));
    }

    private void withdraw() {
        String reauthToken = reauthService
                .reauthenticate("leaving", new ReauthRequest(RAW_PASSWORD, ReauthPurpose.WITHDRAWAL))
                .reauthToken();
        userService.deleteUser("leaving", new DeleteUserRequest(reauthToken));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 탈퇴하면_사용자_행이_실제로_사라진다() {
        withdraw();

        assertThat(userRepository.findByLoginId("leaving")).isEmpty();
        assertThat(userRepository.findById(withdrawing.getId())).isEmpty();
    }

    @Test
    void 탈퇴하면_완료_인증사진과_단독팀_이미지를_outbox에_적재한다() {
        Team soloTeam = teamRepository.save(Team.create("단독 팀", "teams/solo.png", "SOLO0001"));
        teamMemberRepository.save(TeamMember.create(soloTeam, withdrawing, TeamMemberRole.LEADER));
        Todo soloTodo = todoRepository.save(todo(soloTeam, TodoMode.DIRECT, "인증 투두"));
        TodoWorkItem completed = TodoWorkItem.createDirect(soloTodo, withdrawing);
        completed.submit("proofs/original.png", "proofs/thumb.jpg");
        todoWorkItemRepository.save(completed);
        entityManager.flush();

        withdraw();

        assertThat(teamRepository.findById(soloTeam.getId())).isEmpty();
        assertThat(fileDeletionOutboxRepository.findAll())
                .extracting(outbox -> outbox.getObjectKey())
                .contains("profiles/leaving.png", "proofs/original.png", "proofs/thumb.jpg", "teams/solo.png");
    }

    @Test
    void 탈퇴자의_수신알림과_동의이력은_삭제하고_다른_팀원이_받은_알림은_보존한다() {
        notificationRepository.save(Notification.create(
                withdrawing, null, NotificationType.CHAT_MESSAGE, "제목", "내용", 1L));
        notificationRepository.save(Notification.create(
                staying, withdrawing, NotificationType.TODO_CREATED, "제목",
                NotificationActorText.PLACEHOLDER + "님이 만들었습니다.", 1L));
        userConsentRepository.save(UserConsent.create(withdrawing, ConsentType.PRIVACY, "v1"));
        entityManager.flush();

        withdraw();

        assertThat(notificationRepository.findLatestByReceiverId(withdrawing.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(userConsentRepository.findAll()).isEmpty();
        assertThat(notificationRepository.findLatestByReceiverId(staying.getId(), PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getActor()).isNull();
                    assertThat(NotificationResponse.from(notification).content()).isEqualTo("탈퇴한 사용자님이 만들었습니다.");
                });
    }

    @Test
    void 탈퇴자가_만든_투두와_채팅은_남고_작성자만_익명화된다() {
        Todo todo = todoRepository.save(todo(team, TodoMode.DIRECT, "공동 투두"));
        TeamChatMessage message = teamChatMessageRepository.save(TeamChatMessage.create(team, withdrawing, "안녕하세요"));
        entityManager.flush();

        withdraw();

        assertThat(todoRepository.findById(todo.getId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getTitle()).isEqualTo("공동 투두");
            assertThat(reloaded.getCreator()).isNull();
        });
        assertThat(teamChatMessageRepository.findById(message.getId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getContent()).isEqualTo("안녕하세요");
            assertThat(reloaded.getSender()).isNull();
        });
    }

    @Test
    void 완료_WorkItem은_익명화되고_사진키는_null이며_타인의_반응은_보존된다() {
        Todo todo = todoRepository.save(todo(team, TodoMode.DIRECT, "투두"));
        TodoWorkItem completed = TodoWorkItem.createDirect(todo, withdrawing);
        completed.submit("proofs/original.png", "proofs/thumb.png");
        todoWorkItemRepository.save(completed);
        TodoReaction othersReaction = todoReactionRepository.save(
                TodoReaction.create(completed, staying, TodoReactionType.LIKE));
        entityManager.flush();

        withdraw();

        assertThat(todoWorkItemRepository.findById(completed.getId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getAssignee()).isNull();
            assertThat(reloaded.getStatus()).isEqualTo(WorkItemStatus.SUCCESS);
            assertThat(reloaded.getProofImageKey()).isNull();
            assertThat(reloaded.getProofThumbnailKey()).isNull();
        });
        assertThat(todoReactionRepository.findById(othersReaction.getId())).isPresent();
    }

    @Test
    void TASK_진행중_WorkItem은_탈퇴후에도_미배정으로_보존되고_알림도_익명화된다() {
        Todo todo = todoRepository.save(todo(team, TodoMode.TASK, "기말 발표"));
        TodoWorkItem task = todoWorkItemRepository.save(TodoWorkItem.createTask(
                todo, withdrawing, "PPT 만들기", null, LocalDateTime.now().plusHours(2), 0));
        entityManager.flush();

        withdraw();

        assertThat(todoWorkItemRepository.findById(task.getId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getType()).isEqualTo(WorkItemType.TASK);
            assertThat(reloaded.getAssignee()).isNull();
            assertThat(reloaded.getStatus()).isEqualTo(WorkItemStatus.IN_PROGRESS);
        });
        assertThat(notificationRepository.findLatestByReceiverId(staying.getId(), PageRequest.of(0, 10)))
                .anySatisfy(notification -> {
                    assertThat(notification.getType()).isEqualTo(NotificationType.TODO_UNASSIGNED);
                    assertThat(notification.getActor()).isNull();
                    assertThat(notification.getReferenceId()).isEqualTo(todo.getId());
                });
    }

    @Test
    void 다른_담당자가_남은_DIRECT_진행중_WorkItem은_삭제되고_미배정_알림을_남기지_않는다() {
        Todo todo = todoRepository.save(todo(team, TodoMode.DIRECT, "공동 인증"));
        TodoWorkItem leavingItem = todoWorkItemRepository.save(TodoWorkItem.createDirect(todo, withdrawing));
        TodoWorkItem stayingItem = todoWorkItemRepository.save(TodoWorkItem.createDirect(todo, staying));
        entityManager.flush();

        withdraw();

        assertThat(todoWorkItemRepository.findById(leavingItem.getId())).isEmpty();
        assertThat(todoWorkItemRepository.findById(stayingItem.getId())).hasValueSatisfying(
                item -> assertThat(item.getAssignee().getId()).isEqualTo(staying.getId()));
        assertThat(notificationRepository.findLatestByReceiverId(staying.getId(), PageRequest.of(0, 10)))
                .noneMatch(notification -> notification.getType() == NotificationType.TODO_UNASSIGNED);
    }

    @Test
    void 마지막_DIRECT_진행중_WorkItem은_삭제하지_않고_미배정으로_보존한다() {
        Todo todo = todoRepository.save(todo(team, TodoMode.DIRECT, "혼자 인증"));
        TodoWorkItem direct = todoWorkItemRepository.save(TodoWorkItem.createDirect(todo, withdrawing));
        entityManager.flush();

        withdraw();

        assertThat(todoWorkItemRepository.findById(direct.getId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getAssignee()).isNull();
            assertThat(reloaded.getStatus()).isEqualTo(WorkItemStatus.IN_PROGRESS);
        });
        assertThat(todoRepository.findById(todo.getId())).hasValueSatisfying(
                reloaded -> assertThat(reloaded.getStatus()).isEqualTo(TodoStatus.IN_PROGRESS));
    }

    @Test
    void 탈퇴자가_남긴_반응과_읽음상태는_삭제된다() {
        Todo todo = todoRepository.save(todo(team, TodoMode.DIRECT, "투두"));
        TodoWorkItem others = TodoWorkItem.createDirect(todo, staying);
        others.submit("proofs/other.png", "proofs/other-thumb.png");
        todoWorkItemRepository.save(others);
        TodoReaction myReaction = todoReactionRepository.save(
                TodoReaction.create(others, withdrawing, TodoReactionType.HEART));
        teamChatReadStatusRepository.save(TeamChatReadStatus.create(team, withdrawing));
        entityManager.flush();

        withdraw();

        assertThat(todoReactionRepository.findById(myReaction.getId())).isEmpty();
        assertThat(teamChatReadStatusRepository.findByUserIdAndTeamId(withdrawing.getId(), team.getId())).isEmpty();
        assertThat(todoWorkItemRepository.findById(others.getId())).hasValueSatisfying(
                reloaded -> assertThat(reloaded.getAssignee()).isNotNull());
    }

    @Test
    void 팀장이_탈퇴하면_잔여_팀원에게_권한이_넘어가고_팀은_유지된다() {
        teamMemberRepository.findByTeamIdExcludingUser(team.getId(), staying.getId()).get(0).updateRole(TeamMemberRole.LEADER);
        teamMemberRepository.findByTeamIdExcludingUser(team.getId(), withdrawing.getId()).get(0).updateRole(TeamMemberRole.MEMBER);
        entityManager.flush();

        withdraw();

        assertThat(teamRepository.findById(team.getId())).isPresent();
        assertThat(teamMemberRepository.findByTeamIdExcludingUser(team.getId(), withdrawing.getId()))
                .singleElement()
                .satisfies(member -> assertThat(member.getRole()).isEqualTo(TeamMemberRole.LEADER));
    }

    @Test
    void 참조_정리를_빠뜨리면_사용자_삭제가_DB_제약으로_실패한다() {
        notificationRepository.save(Notification.create(
                withdrawing, null, NotificationType.CHAT_MESSAGE, "제목", "내용", 1L));
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findById(withdrawing.getId()).orElseThrow();
        userRepository.delete(reloaded);

        assertThatThrownBy(() -> entityManager.flush())
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("notifications");
    }

    @Test
    void 탈퇴한_아이디와_이메일로_다시_가입할_수_있다() {
        withdraw();

        User rejoined = userRepository.save(User.create(
                "leaving", passwordEncoder.encode(RAW_PASSWORD), "돌아온사람", null));
        rejoined.assignEmail("leaving@example.com");
        entityManager.flush();

        assertThat(userRepository.findByLoginId("leaving")).isPresent();
        assertThat(rejoined.getId()).isNotEqualTo(withdrawing.getId());
    }

    @Test
    void 재인증_없이는_탈퇴할_수_없다() {
        assertThatThrownBy(() -> userService.deleteUser("leaving", new DeleteUserRequest("없는토큰")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        entityManager.clear();
        assertThat(userRepository.findByLoginId("leaving")).isPresent();
    }

    @Test
    void 같은_재인증_토큰으로_두_번_탈퇴할_수_없다() {
        String reauthToken = reauthService
                .reauthenticate("leaving", new ReauthRequest(RAW_PASSWORD, ReauthPurpose.WITHDRAWAL))
                .reauthToken();
        userService.deleteUser("leaving", new DeleteUserRequest(reauthToken));
        entityManager.flush();

        User rejoined = userRepository.save(User.create(
                "leaving", passwordEncoder.encode(RAW_PASSWORD), "돌아온사람", null));
        entityManager.flush();

        assertThatThrownBy(() -> userService.deleteUser("leaving", new DeleteUserRequest(reauthToken)))
                .isInstanceOf(BusinessException.class);
        entityManager.clear();
        assertThat(userRepository.findById(rejoined.getId())).isPresent();
    }

    @Test
    void 탈퇴하면_남아있던_재인증_토큰도_삭제된다() {
        reauthService.reauthenticate("leaving", new ReauthRequest(RAW_PASSWORD, ReauthPurpose.WITHDRAWAL));
        entityManager.flush();

        withdraw();

        assertThat(reauthTokenRepository.findAll()).isEmpty();
    }

    private Todo todo(Team targetTeam, TodoMode mode, String title) {
        return Todo.create(targetTeam, withdrawing, title, null, LocalDateTime.now().plusDays(1), mode);
    }
}
