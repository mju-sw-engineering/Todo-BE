package com.todo.domain.user.service;

import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.domain.auth.repository.ReauthTokenRepository;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.service.TeamService;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.file.service.FileDeletionOutboxService;
import com.todo.global.mail.repository.MailOutboxRepository;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String REAUTH_TOKEN = "reauth-token";

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private FileService fileService;
    @Mock
    private TeamService teamService;
    @Mock
    private TodoParticipantRepository todoParticipantRepository;
    @Mock
    private TodoReactionRepository todoReactionRepository;
    @Mock
    private TeamChatMessageRepository teamChatMessageRepository;
    @Mock
    private TeamChatReadStatusRepository teamChatReadStatusRepository;
    @Mock
    private TodoRepository todoRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserConsentRepository userConsentRepository;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private MailOutboxRepository mailOutboxRepository;
    @Mock
    private ReauthTokenRepository reauthTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ReauthService reauthService;
    @Mock
    private FileDeletionOutboxService fileDeletionOutboxService;

    @Test
    void 마이페이지_조회_성공_소속팀없음() {
        User user = User.create("user1", "encodedPwd", "닉네임", "profiles/user1.png");
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());
        given(fileService.resolveImageUrl("profiles/user1.png")).willReturn("https://example.com/profiles/user1.png");

        MyPageResponse response = userService.getMyPage("user1");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.loginId()).isEqualTo("user1");
        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profiles/user1.png");
        assertThat(response.teams()).isEmpty();
    }

    @Test
    void 마이페이지_조회_성공_소속팀있음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", "teams/study.png", "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 10L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of(team));
        given(fileService.resolveImageUrl("teams/study.png")).willReturn("https://example.com/teams/study.png");

        MyPageResponse response = userService.getMyPage("user1");

        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.teams()).hasSize(1);
        assertThat(response.teams().get(0).teamId()).isEqualTo(10L);
        assertThat(response.teams().get(0).teamName()).isEqualTo("스터디 팀");
        assertThat(response.teams().get(0).teamImageUrl()).isEqualTo("https://example.com/teams/study.png");
    }

    @Test
    void 닉네임_수정_성공() {
        User user = User.create("user1", "encodedPwd", "기존닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());

        MyPageResponse response = userService.updateNickname("user1", new UpdateNicknameRequest("새닉네임"));

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(response.nickname()).isEqualTo("새닉네임");
    }

    @Test
    void 마이페이지_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyPage("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 닉네임_수정_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateNickname("unknown", new UpdateNicknameRequest("새닉네임")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private User withdrawingUser() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private void givenWithdrawingUserFound(User user) {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
    }

    @Test
    void 회원탈퇴_성공_생성한_투두와_채팅은_삭제하지_않고_작성자만_익명화한다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(todoRepository).clearCreatorByUserId(1L);
        verify(teamChatMessageRepository).clearSenderByUserId(1L);
        verify(todoRepository, never()).deleteByIdIn(anyList());
    }

    @Test
    void 회원탈퇴_성공_완료_참가기록은_익명화하고_진행중_배정만_삭제한다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of(100L));
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of(1000L));

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        // 삭제 대상 참가 기록의 반응을 먼저 지워야 todo_reactions FK(RESTRICT)에 걸리지 않는다.
        var inOrder = inOrder(todoReactionRepository, todoParticipantRepository);
        inOrder.verify(todoReactionRepository).deleteByTodoParticipantIdIn(List.of(1000L));
        inOrder.verify(todoParticipantRepository).anonymizeFinishedByUserId(1L);
        inOrder.verify(todoParticipantRepository).deleteInProgressByUserId(1L);
    }

    @Test
    void 회원탈퇴_성공_진행중_배정이_없으면_반응_삭제와_재평가를_건너뛴다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(todoReactionRepository, never()).deleteByTodoParticipantIdIn(anyList());
        verify(todoRepository, never()).markAsFailWhenNoParticipantsRemain(anyList());
        verify(todoRepository, never()).markAsSuccessWhenRemainingAllSucceeded(anyList());
    }

    @Test
    void 회원탈퇴_성공_참가자가_빠진_투두의_상태를_재평가한다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of(100L, 101L));
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of(1000L));

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        // 잔여 0명 → FAIL, 잔여 전원 성공 → SUCCESS. 참가자 제거가 끝난 뒤에 평가해야 한다.
        var inOrder = inOrder(todoParticipantRepository, todoRepository);
        inOrder.verify(todoParticipantRepository).deleteInProgressByUserId(1L);
        inOrder.verify(todoRepository).markAsFailWhenNoParticipantsRemain(List.of(100L, 101L));
        inOrder.verify(todoRepository).markAsSuccessWhenRemainingAllSucceeded(List.of(100L, 101L));
    }

    @Test
    void 회원탈퇴_성공_개인정보와_FK_참조를_모두_정리한다() {
        User user = withdrawingUser();
        user.assignEmail("user1@example.com");
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(notificationRepository).deleteByReceiverId(1L);
        verify(teamChatReadStatusRepository).deleteByUserId(1L);
        verify(todoReactionRepository).deleteByUserId(1L);
        verify(userConsentRepository).deleteByUserId(1L);
        verify(emailVerificationRepository).deleteByEmail("user1@example.com");
        verify(mailOutboxRepository).deleteByRecipient("user1@example.com");
        verify(teamMemberRepository).deleteByUserId(1L);
    }

    @Test
    void 회원탈퇴시_프로필과_인증사진을_파일삭제_outbox에_적재한다() {
        User user = User.create("user1", "encodedPwd", "닉네임", "profiles/1/profile.png");
        ReflectionTestUtils.setField(user, "id", 1L);
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findProofImageKeysByUserId(1L))
                .willReturn(List.of("proofs/1/original.png"));
        given(todoParticipantRepository.findProofThumbnailKeysByUserId(1L))
                .willReturn(List.of("proofs/1/thumbs/original.jpg"));
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(fileDeletionOutboxService).enqueueAll(List.of(
                "profiles/1/profile.png",
                "proofs/1/original.png",
                "proofs/1/thumbs/original.jpg"
        ));
    }

    @Test
    void 회원탈퇴_성공_이메일이_없으면_이메일_기준_정리를_건너뛴다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        verifyNoInteractions(emailVerificationRepository, mailOutboxRepository);
        verify(userRepository).delete(user);
    }

    @Test
    void 회원탈퇴_성공_사용자_삭제는_모든_참조_정리_이후에_flush한다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        // flush까지 해야 정리를 빠뜨린 참조가 FK 위반으로 이 트랜잭션 안에서 드러난다.
        var inOrder = inOrder(notificationRepository, userConsentRepository, teamChatMessageRepository,
                todoRepository, todoParticipantRepository, teamMemberRepository, userRepository);
        inOrder.verify(notificationRepository).deleteByReceiverId(1L);
        inOrder.verify(userConsentRepository).deleteByUserId(1L);
        inOrder.verify(teamChatMessageRepository).clearSenderByUserId(1L);
        inOrder.verify(todoRepository).clearCreatorByUserId(1L);
        inOrder.verify(todoParticipantRepository).anonymizeFinishedByUserId(1L);
        inOrder.verify(teamMemberRepository).deleteByUserId(1L);
        inOrder.verify(userRepository).delete(user);
        inOrder.verify(userRepository).flush();
    }

    @Test
    void 회원탈퇴_성공_혼자_있는_리더팀은_팀데이터까지_삭제한다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of(10L));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(teamService).deleteTeamWithAllData(10L);
        verify(userRepository).delete(user);
    }

    @Test
    void 회원탈퇴_성공_잔여_멤버가_있는_리더팀은_가장_먼저_가입한_멤버에게_권한을_넘긴다() {
        User user = withdrawingUser();
        User remaining = User.create("user2", "encodedPwd", "남은사람", null);
        ReflectionTestUtils.setField(remaining, "id", 2L);
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember remainingMember = TeamMember.create(team, remaining, TeamMemberRole.MEMBER);

        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of(10L));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of(remainingMember));
        given(todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(1L)).willReturn(List.of());
        given(todoParticipantRepository.findInProgressIdsByUserId(1L)).willReturn(List.of());

        userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN));

        assertThat(remainingMember.getRole()).isEqualTo(TeamMemberRole.LEADER);
        verify(teamService, never()).deleteTeamWithAllData(10L);
        verify(teamMemberRepository).deleteByUserId(1L);
    }

    @Test
    void 회원탈퇴_실패_중간_정리에서_예외가_나면_사용자를_삭제하지_않는다() {
        User user = withdrawingUser();
        givenWithdrawingUserFound(user);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        willThrow(new RuntimeException("DB 오류")).given(userConsentRepository).deleteByUserId(1L);

        assertThatThrownBy(() -> userService.deleteUser("user1", new DeleteUserRequest(REAUTH_TOKEN)))
                .isInstanceOf(RuntimeException.class);

        verify(userRepository, never()).delete(user);
        verify(userRepository, never()).flush();
    }
}
