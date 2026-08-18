package com.todo.domain.user.service;

import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.domain.auth.repository.PasswordResetTokenRepository;
import com.todo.domain.auth.repository.ReauthTokenRepository;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.auth.event.AppleAccountRevokeRequestedEvent;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.event.TeamMembershipRevokedEvent;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.service.TeamService;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.service.TodoWorkItemLifecycleService;
import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.request.UpdatePasswordRequest;
import com.todo.domain.user.dto.request.UpdateProfileImageRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.dto.response.UserProfileResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.file.service.FileDeletionOutboxService;
import com.todo.global.mail.repository.MailOutboxRepository;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
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
    private TodoWorkItemRepository todoWorkItemRepository;
    @Mock
    private TodoReactionRepository todoReactionRepository;
    @Mock
    private TodoWorkItemLifecycleService todoWorkItemLifecycleService;
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
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ReauthService reauthService;
    @Mock
    private FileDeletionOutboxService fileDeletionOutboxService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.todo.domain.notification.service.NotificationService notificationService;
    @Mock
    private com.todo.domain.notification.message.NotificationMessageFactory notificationMessageFactory;

    @Test
    void 마이페이지_조회_성공_소속팀없음() {
        User user = user(1L, "1", "닉네임", "profiles/user1.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());
        given(fileService.resolveImageUrl("profiles/user1.png")).willReturn("https://example.com/profiles/user1.png");

        MyPageResponse response = userService.getMyPage("1");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.loginId()).isEqualTo("1");
        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profiles/user1.png");
        assertThat(response.teams()).isEmpty();
    }

    @Test
    void 마이페이지_조회_성공_소속팀있음() {
        User user = user(1L, "1", "닉네임", null);
        Team team = Team.create("스터디 팀", "teams/study.png", "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 10L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of(team));
        given(fileService.resolveImageUrl("teams/study.png")).willReturn("https://example.com/teams/study.png");

        MyPageResponse response = userService.getMyPage("1");

        assertThat(response.teams()).singleElement().satisfies(summary -> {
            assertThat(summary.teamId()).isEqualTo(10L);
            assertThat(summary.teamName()).isEqualTo("스터디 팀");
            assertThat(summary.teamImageUrl()).isEqualTo("https://example.com/teams/study.png");
        });
    }

    @Test
    void 닉네임_수정_성공() {
        User user = user(1L, "1", "기존닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());

        MyPageResponse response = userService.updateNickname("1", new UpdateNicknameRequest("새닉네임"));

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(response.nickname()).isEqualTo("새닉네임");
    }

    @Test
    void 프로필_사진_변경_성공_이전_이미지가_있으면_삭제_예약한다() {
        User user = user(1L, "1", "닉네임", "profiles/1/old.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());
        given(fileService.resolveImageUrl("profiles/1/new.png")).willReturn("https://example.com/profiles/1/new.png");

        MyPageResponse response = userService.updateProfileImage("1", new UpdateProfileImageRequest("profiles/1/new.png"));

        assertThat(user.getProfileImageUrl()).isEqualTo("profiles/1/new.png");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profiles/1/new.png");
        verify(fileDeletionOutboxService).enqueueAll(List.of("profiles/1/old.png"));
    }

    @Test
    void 프로필_사진_변경은_이전_이미지가_없으면_삭제를_예약하지_않는다() {
        User user = user(1L, "1", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());

        userService.updateProfileImage("1", new UpdateProfileImageRequest("profiles/1/new.png"));

        verify(fileDeletionOutboxService, never()).enqueueAll(anyList());
    }

    @Test
    void 프로필_사진_변경은_같은_키를_다시_보내면_삭제를_예약하지_않는다() {
        User user = user(1L, "1", "닉네임", "profiles/1/same.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());

        userService.updateProfileImage("1", new UpdateProfileImageRequest("profiles/1/same.png"));

        verify(fileDeletionOutboxService, never()).enqueueAll(anyList());
    }

    @Test
    void 프로필_사진_변경은_본인이_업로드하지_않은_키면_거부하고_아무것도_바꾸지_않는다() {
        User user = user(1L, "1", "닉네임", "profiles/1/old.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        org.mockito.BDDMockito.willThrow(new BusinessException("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.", HttpStatus.BAD_REQUEST))
                .given(fileService).validateProfileImageKey(1L, "proofs/2/stolen.jpg");

        assertThatThrownBy(() -> userService.updateProfileImage("1", new UpdateProfileImageRequest("proofs/2/stolen.jpg")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.");

        assertThat(user.getProfileImageUrl()).isEqualTo("profiles/1/old.png");
        verify(fileDeletionOutboxService, never()).enqueueAll(anyList());
    }

    @Test
    void 프로필_사진_변경_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfileImage("999", new UpdateProfileImageRequest("profiles/1/new.png")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 마이페이지_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyPage("999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 닉네임_수정_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateNickname("999", new UpdateNicknameRequest("새닉네임")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 프로필_조회는_팀_조회_없이_경량_응답을_반환한다() {
        User user = user(1L, "1", "닉네임", "profiles/user1.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(fileService.resolveImageUrl("profiles/user1.png")).willReturn("https://example.com/profiles/user1.png");

        UserProfileResponse response = userService.getMyProfile("1");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.loginId()).isEqualTo("1");
        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profiles/user1.png");
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    void 프로필_조회는_프로필_이미지가_없어도_정상_동작한다() {
        User user = user(1L, "1", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(fileService.resolveImageUrl(null)).willReturn(null);

        UserProfileResponse response = userService.getMyProfile("1");

        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void 프로필_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile("999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 회원탈퇴는_팀별_진행중_WorkItem_정리와_완료기록_익명화를_위임한다() {
        User withdrawing = withdrawingUser();
        Team firstTeam = team(10L);
        Team secondTeam = team(20L);
        givenWithdrawalUser(withdrawing);
        given(teamMemberRepository.findTeamsByUserId(withdrawing.getId())).willReturn(List.of(firstTeam, secondTeam));

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        InOrder order = inOrder(todoWorkItemLifecycleService);
        order.verify(todoWorkItemLifecycleService).handleTeamDeparture(10L, withdrawing);
        order.verify(todoWorkItemLifecycleService).handleTeamDeparture(20L, withdrawing);
        order.verify(todoWorkItemLifecycleService).anonymizeFinishedForWithdrawal(1L);
    }

    @Test
    void 회원탈퇴는_공동_투두와_채팅을_지우지_않고_작성자만_익명화한다() {
        User withdrawing = withdrawingUser();
        givenWithdrawalUser(withdrawing);

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(todoRepository).clearCreatorByUserId(1L);
        verify(teamChatMessageRepository).clearSenderByUserId(1L);
        verify(todoWorkItemLifecycleService).anonymizeFinishedForWithdrawal(1L);
        verify(todoRepository, never()).deleteByIdIn(anyList());
    }

    @Test
    void 회원탈퇴는_완료사진_키를_outbox에_넣은_뒤_완료기록을_익명화한다() {
        User withdrawing = user(1L, "1", "닉네임", "profiles/1/profile.png");
        givenWithdrawalUser(withdrawing);
        given(todoWorkItemRepository.findProofImageKeysByAssigneeId(1L)).willReturn(List.of("proofs/1/original.png"));
        given(todoWorkItemRepository.findProofThumbnailKeysByAssigneeId(1L)).willReturn(List.of("proofs/1/thumb.jpg"));

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(fileDeletionOutboxService).enqueueAll(List.of(
                "profiles/1/profile.png", "proofs/1/original.png", "proofs/1/thumb.jpg"
        ));
        verify(todoWorkItemLifecycleService).anonymizeFinishedForWithdrawal(1L);
    }

    @Test
    void 회원탈퇴는_수신알림을_삭제하고_다른_팀원이_받은_알림의_actor를_익명화한다() {
        User withdrawing = withdrawingUser();
        givenWithdrawalUser(withdrawing);

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        InOrder order = inOrder(notificationRepository);
        order.verify(notificationRepository).deleteByReceiverId(1L);
        order.verify(notificationRepository).clearActorByUserId(1L);
    }

    @Test
    void 회원탈퇴는_개인정보와_FK_참조를_모두_정리한다() {
        User withdrawing = withdrawingUser();
        withdrawing.assignEmail("user1@example.com");
        givenWithdrawalUser(withdrawing);

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(reauthService).consume("1", REAUTH_TOKEN, ReauthPurpose.WITHDRAWAL);
        verify(teamChatReadStatusRepository).deleteByUserId(1L);
        verify(todoReactionRepository).deleteByUserId(1L);
        verify(userConsentRepository).deleteByUserId(1L);
        verify(reauthTokenRepository).deleteByUserId(1L);
        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(emailVerificationRepository).deleteByEmail("user1@example.com");
        verify(mailOutboxRepository).deleteByRecipient("user1@example.com");
        verify(teamMemberRepository).deleteByUserId(1L);
    }

    @Test
    void 회원탈퇴는_모든_참조_정리_이후에_사용자를_삭제하고_flush한다() {
        User withdrawing = withdrawingUser();
        givenWithdrawalUser(withdrawing);

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        InOrder order = inOrder(notificationRepository, userConsentRepository, teamChatMessageRepository,
                todoRepository, todoWorkItemLifecycleService, teamMemberRepository, userRepository);
        order.verify(notificationRepository).deleteByReceiverId(1L);
        order.verify(userConsentRepository).deleteByUserId(1L);
        order.verify(todoWorkItemLifecycleService).anonymizeFinishedForWithdrawal(1L);
        order.verify(teamChatMessageRepository).clearSenderByUserId(1L);
        order.verify(todoRepository).clearCreatorByUserId(1L);
        order.verify(notificationRepository).clearActorByUserId(1L);
        order.verify(teamMemberRepository).deleteByUserId(1L);
        order.verify(userRepository).delete(withdrawing);
        order.verify(userRepository).flush();
    }

    @Test
    void 회원탈퇴는_혼자_있는_리더팀을_팀데이터와_함께_삭제한다() {
        User withdrawing = withdrawingUser();
        givenWithdrawalUser(withdrawing);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of(10L));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(teamService).deleteTeamWithAllData(10L);
        verify(userRepository).delete(withdrawing);
    }

    @Test
    void 회원탈퇴는_남은_리더팀의_가장_먼저_가입한_멤버에게_권한을_넘긴다() {
        User withdrawing = withdrawingUser();
        User remaining = user(2L, "2", "남은사람", null);
        TeamMember remainingMember = TeamMember.create(team(10L), remaining, TeamMemberRole.MEMBER);
        givenWithdrawalUser(withdrawing);
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of(10L));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of(remainingMember));

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        assertThat(remainingMember.getRole()).isEqualTo(TeamMemberRole.LEADER);
        verify(teamService, never()).deleteTeamWithAllData(10L);
    }

    @Test
    void 회원탈퇴_중간_정리에서_예외가_나면_사용자를_삭제하지_않는다() {
        User withdrawing = withdrawingUser();
        givenWithdrawalUser(withdrawing);
        willThrow(new RuntimeException("DB 오류")).given(userConsentRepository).deleteByUserId(1L);

        assertThatThrownBy(() -> userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN)))
                .isInstanceOf(RuntimeException.class);

        verify(userRepository, never()).delete(withdrawing);
        verify(userRepository, never()).flush();
    }

    @Test
    void 회원탈퇴는_Apple_유저면_저장된_client_id로_revoke_이벤트를_발행한다() {
        User withdrawing = appleWithdrawingUser("apple-rt", "com.test.app");
        givenWithdrawalUser(withdrawing);

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(eventPublisher).publishEvent(new AppleAccountRevokeRequestedEvent(1L, "apple-rt", "com.test.app"));
        verify(userRepository).delete(withdrawing);
    }

    @Test
    void 회원탈퇴는_이메일_유저면_revoke_이벤트를_발행하지_않는다() {
        User withdrawing = withdrawingUser();
        givenWithdrawalUser(withdrawing);

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(eventPublisher, never()).publishEvent(any(AppleAccountRevokeRequestedEvent.class));
    }

    @Test
    void 회원탈퇴는_WebSocket_세션_종료를_위한_멤버십_상실_이벤트를_발행한다() {
        User withdrawing = withdrawingUser();
        givenWithdrawalUser(withdrawing);

        userService.deleteUser("1", new DeleteUserRequest(REAUTH_TOKEN));

        verify(eventPublisher).publishEvent(new TeamMembershipRevokedEvent(1L));
    }

    @Test
    void 비밀번호_변경_성공() {
        User user = user(1L, "1", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("currentPwd", "encodedPwd")).willReturn(true);
        given(passwordEncoder.encode("newPwd1!")).willReturn("encodedNewPwd");

        userService.updatePassword("1", new UpdatePasswordRequest("currentPwd", "newPwd1!", "newPwd1!"));

        assertThat(user.getPassword()).isEqualTo("encodedNewPwd");
        // 탈취 의심으로 바꾸는 경우를 위해 기존 세션을 전부 무효화한다
        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    void 비밀번호_변경은_Apple_계정이면_거부한다() {
        User apple = appleWithdrawingUser("apple-rt", "com.test.app");
        given(userRepository.findById(1L)).willReturn(Optional.of(apple));

        assertThatThrownBy(() -> userService.updatePassword("1", new UpdatePasswordRequest("currentPwd", "newPwd1!", "newPwd1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apple 계정");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void 비밀번호_변경은_현재_비밀번호가_틀리면_거부한다() {
        User user = user(1L, "1", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPwd", "encodedPwd")).willReturn(false);

        assertThatThrownBy(() -> userService.updatePassword("1", new UpdatePasswordRequest("wrongPwd", "newPwd1!", "newPwd1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("현재 비밀번호가 일치하지 않습니다.");
        assertThat(user.getPassword()).isEqualTo("encodedPwd");
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void 비밀번호_변경은_새_비밀번호_확인이_다르면_거부한다() {
        User user = user(1L, "1", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("currentPwd", "encodedPwd")).willReturn(true);

        assertThatThrownBy(() -> userService.updatePassword("1", new UpdatePasswordRequest("currentPwd", "newPwd1!", "다름")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
        assertThat(user.getPassword()).isEqualTo("encodedPwd");
    }

    private User withdrawingUser() {
        return user(1L, "1", "닉네임", null);
    }

    private User appleWithdrawingUser(String appleRefreshToken, String appleClientId) {
        User user = User.createAppleUser("apple-social-1", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        user.saveAppleCredentials(appleRefreshToken, appleClientId);
        return user;
    }

    private User user(Long id, String loginId, String nickname, String profileImageUrl) {
        User user = User.create(loginId, "encodedPwd", nickname, profileImageUrl);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Team team(Long id) {
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private void givenWithdrawalUser(User user) {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
    }
}
