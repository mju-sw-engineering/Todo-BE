package com.todo.domain.user.service;

import com.todo.domain.auth.dto.request.ReauthRequest;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.repository.ReauthTokenRepository;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.chat.entity.TeamChatReadStatus;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.dto.response.NotificationResponse;
import com.todo.domain.notification.entity.Notification;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.notification.message.NotificationActorText;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 엔티티 그래프와 FK 제약이 살아 있는 상태에서 탈퇴 전체 흐름을 검증한다.
 *
 * <p>단위 테스트는 mock이라 FK 위반을 잡지 못한다. 참조 정리를 하나라도 빠뜨리면
 * 마지막 users 삭제가 실패해야 하는데, 그 계약을 확인할 수 있는 유일한 계층이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserWithdrawalIntegrationTest {

    private static final String RAW_PASSWORD = "rawPassword123!";

    @Autowired
    private UserService userService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private TodoRepository todoRepository;
    @Autowired
    private TodoParticipantRepository todoParticipantRepository;
    @Autowired
    private TodoReactionRepository todoReactionRepository;
    @Autowired
    private TeamChatMessageRepository teamChatMessageRepository;
    @Autowired
    private TeamChatReadStatusRepository teamChatReadStatusRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserConsentRepository userConsentRepository;
    @Autowired
    private ReauthService reauthService;
    @Autowired
    private ReauthTokenRepository reauthTokenRepository;
    @Autowired
    private FileDeletionOutboxRepository fileDeletionOutboxRepository;

    private User withdrawing;
    private User staying;
    private Team team;

    @BeforeEach
    void setUp() {
        withdrawing = userRepository.save(
                User.create("leaving", passwordEncoder.encode(RAW_PASSWORD), "떠나는사람", "profiles/leaving.png"));
        withdrawing.assignEmail("leaving@example.com");
        staying = userRepository.save(
                User.create("staying", passwordEncoder.encode(RAW_PASSWORD), "남는사람", null));

        team = teamRepository.save(Team.create("팀", null, "INVITE01"));
        teamMemberRepository.save(TeamMember.create(team, staying, TeamMemberRole.LEADER));
        teamMemberRepository.save(TeamMember.create(team, withdrawing, TeamMemberRole.MEMBER));
    }

    /**
     * 실제 재인증을 거쳐 탈퇴한다. 토큰 발급과 소비까지 함께 검증된다.
     */
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
    void 탈퇴하면_프로필과_인증사진과_단독팀_이미지를_파일삭제_outbox에_적재한다() {
        Team soloTeam = teamRepository.save(Team.create("단독 팀", "teams/solo.png", "SOLO0001"));
        teamMemberRepository.save(TeamMember.create(soloTeam, withdrawing, TeamMemberRole.LEADER));
        Todo todo = todoRepository.save(
                Todo.create(soloTeam, withdrawing, "인증 투두", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant participant = TodoParticipant.create(todo, withdrawing);
        participant.submit("proofs/original.png", "proofs/thumb.jpg");
        todoParticipantRepository.save(participant);
        entityManager.flush();

        withdraw();

        assertThat(teamRepository.findById(soloTeam.getId())).isEmpty();
        assertThat(fileDeletionOutboxRepository.findAll())
                .extracting(outbox -> outbox.getObjectKey())
                .contains(
                        "profiles/leaving.png",
                        "proofs/original.png",
                        "proofs/thumb.jpg",
                        "teams/solo.png"
                );
    }

    @Test
    void 알림과_동의_이력이_있어도_FK_위반으로_실패하지_않는다() {
        notificationRepository.save(Notification.create(
                withdrawing, null, NotificationType.CHAT_MESSAGE, "제목", "내용", 1L));
        userConsentRepository.save(UserConsent.create(withdrawing, ConsentType.PRIVACY, "v1"));
        entityManager.flush();

        withdraw();

        assertThat(userRepository.findById(withdrawing.getId())).isEmpty();
        assertThat(notificationRepository.findLatestByReceiverId(withdrawing.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(userConsentRepository.findAll()).isEmpty();
    }

    @Test
    void 탈퇴자가_유발한_알림은_남고_행위자만_익명화된다() {
        notificationRepository.save(Notification.create(
                staying,
                withdrawing,
                NotificationType.TODO_CREATED,
                "새로운 투두가 생성되었습니다.",
                NotificationActorText.PLACEHOLDER + "님이 '공동 투두'을(를) 만들었습니다.",
                10L
        ));
        entityManager.flush();

        withdraw();

        List<Notification> remaining = notificationRepository.findLatestByReceiverId(
                staying.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getActor()).isNull();
        assertThat(NotificationResponse.from(remaining.get(0)).content())
                .isEqualTo("탈퇴한 사용자님이 '공동 투두'을(를) 만들었습니다.");
    }

    @Test
    void 탈퇴자가_만든_투두는_남고_생성자만_익명화된다() {
        Todo todo = todoRepository.save(
                Todo.create(team, withdrawing, "공동 투두", null, LocalDateTime.now().plusDays(1)));
        entityManager.flush();

        withdraw();

        Todo reloaded = todoRepository.findById(todo.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("공동 투두");
        assertThat(reloaded.getCreator()).isNull();
    }

    @Test
    void 탈퇴자가_보낸_채팅은_남고_발신자만_익명화된다() {
        TeamChatMessage message = teamChatMessageRepository.save(
                TeamChatMessage.create(team, withdrawing, "안녕하세요"));
        entityManager.flush();

        withdraw();

        TeamChatMessage reloaded = teamChatMessageRepository.findById(message.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("안녕하세요");
        assertThat(reloaded.getSender()).isNull();
    }

    @Test
    void 완료된_참가기록은_익명으로_남고_타인의_반응도_보존된다() {
        Todo todo = todoRepository.save(
                Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant finished = TodoParticipant.create(todo, withdrawing);
        finished.submit("proofs/original.png", "proofs/thumb.png");
        todoParticipantRepository.save(finished);
        TodoReaction othersReaction = todoReactionRepository.save(
                TodoReaction.create(finished, staying, TodoReactionType.LIKE));
        entityManager.flush();

        withdraw();

        TodoParticipant reloaded = todoParticipantRepository.findById(finished.getId()).orElseThrow();
        assertThat(reloaded.getUser()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(ParticipantStatus.SUCCESS);
        assertThat(reloaded.getProofImageKey()).isNull();
        assertThat(todoReactionRepository.findById(othersReaction.getId())).isPresent();
    }

    @Test
    void 진행중_배정은_제거되고_남은_참가자가_없으면_투두가_FAIL된다() {
        Todo todo = todoRepository.save(
                Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant inProgress = todoParticipantRepository.save(TodoParticipant.create(todo, withdrawing));
        entityManager.flush();

        withdraw();

        assertThat(todoParticipantRepository.findById(inProgress.getId())).isEmpty();
        assertThat(todoRepository.findById(todo.getId()).orElseThrow().getStatus())
                .isEqualTo(com.todo.domain.todo.entity.TodoStatus.FAIL);
    }

    @Test
    void 탈퇴자가_남긴_반응과_읽음상태는_삭제된다() {
        Todo todo = todoRepository.save(
                Todo.create(team, staying, "투두", null, LocalDateTime.now().plusDays(1)));
        TodoParticipant others = TodoParticipant.create(todo, staying);
        others.submit("proofs/other.png", "proofs/other-thumb.png");
        todoParticipantRepository.save(others);
        TodoReaction myReaction = todoReactionRepository.save(
                TodoReaction.create(others, withdrawing, TodoReactionType.HEART));
        teamChatReadStatusRepository.save(TeamChatReadStatus.create(team, withdrawing));
        entityManager.flush();

        withdraw();

        assertThat(todoReactionRepository.findById(myReaction.getId())).isEmpty();
        assertThat(teamChatReadStatusRepository.findByUserIdAndTeamId(withdrawing.getId(), team.getId())).isEmpty();
        // 익명화 대상이 아닌 타인의 참가 기록은 그대로 남는다.
        assertThat(todoParticipantRepository.findById(others.getId()).orElseThrow().getUser()).isNotNull();
    }

    @Test
    void 팀장이_탈퇴하면_잔여_팀원에게_권한이_넘어가고_팀은_유지된다() {
        List<TeamMember> members = teamMemberRepository.findByTeamIdExcludingUser(team.getId(), staying.getId());
        members.get(0).updateRole(TeamMemberRole.LEADER);
        teamMemberRepository.findByTeamIdExcludingUser(team.getId(), withdrawing.getId())
                .get(0).updateRole(TeamMemberRole.MEMBER);
        entityManager.flush();

        withdraw();

        assertThat(teamRepository.findById(team.getId())).isPresent();
        List<TeamMember> remaining = teamMemberRepository.findByTeamIdExcludingUser(team.getId(), withdrawing.getId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getRole()).isEqualTo(TeamMemberRole.LEADER);
    }

    @Test
    void 참조_정리를_빠뜨리면_사용자_삭제가_DB_제약으로_실패한다() {
        notificationRepository.save(Notification.create(
                withdrawing, null, NotificationType.CHAT_MESSAGE, "제목", "내용", 1L));
        entityManager.flush();
        // 영속성 컨텍스트를 비워 Hibernate의 TransientObjectException이 아니라
        // 실제 DB의 RESTRICT FK가 막는지 확인한다.
        entityManager.clear();

        User reloaded = userRepository.findById(withdrawing.getId()).orElseThrow();
        userRepository.delete(reloaded);

        // 이 안전망이 있어야 정리 단계 누락이 조용한 데이터 손실이 아니라 즉시 오류로 드러난다.
        assertThatThrownBy(() -> entityManager.flush())
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("notifications");
    }

    @Test
    void 탈퇴한_아이디와_이메일로_다시_가입할_수_있다() {
        withdraw();

        User rejoined = userRepository.save(
                User.create("leaving", passwordEncoder.encode(RAW_PASSWORD), "돌아온사람", null));
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

        User rejoined = userRepository.save(
                User.create("leaving", passwordEncoder.encode(RAW_PASSWORD), "돌아온사람", null));
        entityManager.flush();

        // 1회용이므로 재가입한 계정에도 같은 토큰을 재사용할 수 없다.
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

        // reauth_tokens.user_id FK가 RESTRICT라 이 정리를 빠뜨리면 탈퇴가 실패한다.
        assertThat(reauthTokenRepository.findAll()).isEmpty();
    }
}
