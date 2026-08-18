package com.todo.domain.team.service;

import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.InviteTeamRequest;
import com.todo.domain.team.dto.request.JoinByInviteLinkRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.InviteLinkResponse;
import com.todo.domain.team.dto.response.InviteTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.event.TeamMembershipRevokedEvent;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.service.TodoWorkItemLifecycleService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.file.service.FileDeletionOutboxService;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @InjectMocks
    private TeamService teamService;

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileService fileService;
    @Mock
    private TeamInviteMailService teamInviteMailService;
    @Mock
    private TodoRepository todoRepository;
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
    private FileDeletionOutboxService fileDeletionOutboxService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void 팀_생성_성공_이미지없음() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.createTeam("1", new CreateTeamRequest("우리팀", null, null));

        assertThat(response.teamName()).isEqualTo("우리팀");
        assertThat(response.teamImage()).isNull();
        assertThat(response.inviteCode()).hasSize(8);
    }

    @Test
    void 팀_생성_성공_이미지있음() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        String imageKey = "teams/temp/uuid.jpg";
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(fileService.resolveImageUrl(imageKey)).willReturn("https://minio.example.com/image.jpg");

        CreateTeamResponse response = teamService.createTeam("1", new CreateTeamRequest("우리팀", null, imageKey));

        assertThat(response.teamImage()).isEqualTo("https://minio.example.com/image.jpg");
    }

    @Test
    void 팀_생성은_본인이_업로드하지_않은_이미지_키면_거부하고_저장하지_않는다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        org.mockito.BDDMockito.willThrow(
                        new BusinessException("본인이 업로드한 팀 이미지만 사용할 수 있습니다.", HttpStatus.BAD_REQUEST))
                .given(fileService).validateTeamImageKey(user.getId(), "proofs/2/stolen.jpg");

        assertThatThrownBy(() -> teamService.createTeam("1", new CreateTeamRequest("우리팀", null, "proofs/2/stolen.jpg")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 팀 이미지만 사용할 수 있습니다.");

        then(teamRepository).should(never()).save(any(Team.class));
    }

    @Test
    void 팀_생성_시_설명이_저장된다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.createTeam(
                "1", new CreateTeamRequest("우리팀", "  매일 한 문제씩 푸는 스터디  ", null));

        // 앞뒤 공백은 정리해 저장한다
        assertThat(response.description()).isEqualTo("매일 한 문제씩 푸는 스터디");
    }

    @Test
    void 공백뿐인_설명은_저장하지_않는다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.createTeam(
                "1", new CreateTeamRequest("우리팀", "   ", null));

        assertThat(response.description()).isNull();
    }

    @Test
    void 팀_생성_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.createTeam("999", new CreateTeamRequest("팀", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용자를 찾을 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_생성_시_팀장으로_등록된다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        teamService.createTeam("1", new CreateTeamRequest("우리팀", null, null));

        then(teamMemberRepository).should().save(argThat(member -> member.getRole() == TeamMemberRole.LEADER));
    }

    @Test
    void 초대코드_중복시_재시도하여_고유코드_생성() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString()))
                .willReturn(true)   // 1회 중복
                .willReturn(false); // 2회 성공
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.createTeam("1", new CreateTeamRequest("우리팀", null, null));

        assertThat(response.inviteCode()).hasSize(8);
    }

    @Test
    void 내_팀_목록_조회_성공_팀없음() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());

        TeamListResponse response = teamService.getMyTeams("1");

        assertThat(response.teams()).isEmpty();
    }

    @Test
    void 내_팀_목록_조회_성공_팀있음() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team studyTeam = Team.create("스터디 팀", "https://example.com/team1.png", "ABCDEFGH");
        ReflectionTestUtils.setField(studyTeam, "id", 10L);
        Team exerciseTeam = Team.create("운동 팀", null, "IJKLMNOP");
        ReflectionTestUtils.setField(exerciseTeam, "id", 20L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of(studyTeam, exerciseTeam));
        given(fileService.resolveImageUrl("https://example.com/team1.png")).willReturn("https://example.com/team1.png");

        TeamListResponse response = teamService.getMyTeams("1");

        assertThat(response.teams()).hasSize(2);
        assertThat(response.teams().get(0).teamId()).isEqualTo(10L);
        assertThat(response.teams().get(0).teamName()).isEqualTo("스터디 팀");
        assertThat(response.teams().get(0).teamImageUrl()).isEqualTo("https://example.com/team1.png");
        assertThat(response.teams().get(1).teamId()).isEqualTo(20L);
        assertThat(response.teams().get(1).teamName()).isEqualTo("운동 팀");
        assertThat(response.teams().get(1).teamImageUrl()).isNull();
    }

    @Test
    void 팀_상세_조회_성공() {
        User user = User.create("1", "encodedPwd", "홍길동", "https://example.com/profile1.png");
        ReflectionTestUtils.setField(user, "id", 1L);
        User member2 = User.create("2", "encodedPwd", "김철수", null);
        ReflectionTestUtils.setField(member2, "id", 2L);

        Team team = Team.create("스터디 팀", "https://example.com/team.png", "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);

        TeamMember leader = TeamMember.create(team, user, TeamMemberRole.LEADER);
        TeamMember memberEntry = TeamMember.create(team, member2, TeamMemberRole.MEMBER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(true);
        given(teamMemberRepository.findByTeamIdWithUser(1L)).willReturn(List.of(leader, memberEntry));
        given(fileService.resolveImageUrl(null)).willReturn(null);
        given(fileService.resolveImageUrl("https://example.com/team.png")).willReturn("https://example.com/team.png");
        given(fileService.resolveImageUrl("https://example.com/profile1.png")).willReturn("https://example.com/profile1.png");

        TeamDetailResponse response = teamService.getTeamDetail(1L, "1");

        assertThat(response.teamId()).isEqualTo(1L);
        assertThat(response.teamName()).isEqualTo("스터디 팀");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members()).hasSize(2);
        assertThat(response.members().get(0).role()).isEqualTo("LEADER");
        assertThat(response.members().get(1).role()).isEqualTo("MEMBER");
        assertThat(response.members().get(1).profileImageUrl()).isNull();
    }

    @Test
    void 팀_상세_조회_실패_존재하지_않는_팀() {
        User user = User.create("1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamDetail(99L, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 팀입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_상세_조회_실패_팀에_속하지_않은_사용자() {
        User user = User.create("1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 팀_상세_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, "999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 내_팀_목록_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getMyTeams("999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_참여_성공() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        User existingMember = User.create("2", "pw", "기존팀원", null);
        ReflectionTestUtils.setField(existingMember, "id", 2L);
        TeamMember existing = TeamMember.create(team, existingMember, TeamMemberRole.LEADER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findByInviteCode("ABCD1234")).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(false);
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of(existing));
        NotificationMessage message = new NotificationMessage(NotificationType.TEAM_MEMBER_JOINED, "title", "content");
        given(notificationMessageFactory.teamMemberJoined()).willReturn(message);

        JoinTeamResponse response = teamService.joinTeam("1", new JoinTeamRequest("ABCD1234"));

        assertThat(response.teamId()).isEqualTo(10L);
        then(teamMemberRepository).should().save(argThat(m -> m.getRole() == TeamMemberRole.MEMBER));
        then(notificationService).should().sendAll(List.of(existingMember), user, message, 10L);
    }

    @Test
    void 팀_참여_실패_유효하지_않은_초대_코드() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findByInviteCode("INVALID")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinTeam("1", new JoinTeamRequest("INVALID")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않은 초대 코드입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_참여_실패_이미_참여한_팀() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findByInviteCode("ABCD1234")).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);

        assertThatThrownBy(() -> teamService.joinTeam("1", new JoinTeamRequest("ABCD1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 참여한 팀입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 팀_참여_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinTeam("999", new JoinTeamRequest("ABCD1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 초대_링크가_없으면_새로_발급한다() {
        setupTeamInviteLinkProperties();
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        InviteLinkResponse response = teamService.getOrCreateInviteLink("1", 10L);

        assertThat(response.inviteLink()).startsWith("https://backend.example.com/invite?token=");
        assertThat(team.getInviteLinkToken()).isNotBlank();
        assertThat(response.expiresAt()).isAfter(java.time.OffsetDateTime.now().plusDays(6));
    }

    @Test
    void 유효한_초대_링크가_있으면_재발급하지_않는다() {
        setupTeamInviteLinkProperties();
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        team.updateInviteLink("existing-token", java.time.LocalDateTime.now().plusDays(3));
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        InviteLinkResponse response = teamService.getOrCreateInviteLink("1", 10L);

        assertThat(response.inviteLink()).isEqualTo("https://backend.example.com/invite?token=existing-token");
        assertThat(team.getInviteLinkToken()).isEqualTo("existing-token");
    }

    @Test
    void 만료된_초대_링크는_재발급한다() {
        setupTeamInviteLinkProperties();
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        team.updateInviteLink("expired-token", java.time.LocalDateTime.now().minusSeconds(1));
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        teamService.getOrCreateInviteLink("1", 10L);

        assertThat(team.getInviteLinkToken()).isNotEqualTo("expired-token");
    }

    @Test
    void 초대_링크_발급은_팀_멤버가_아니면_거부한다() {
        setupTeamInviteLinkProperties();
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getOrCreateInviteLink("1", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 초대_링크_토큰으로_참여한다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        team.updateInviteLink("valid-token", java.time.LocalDateTime.now().plusDays(3));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findByInviteLinkToken("valid-token")).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(false);
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        JoinTeamResponse response = teamService.joinTeamByInviteLink("1", new JoinByInviteLinkRequest("valid-token"));

        assertThat(response.teamId()).isEqualTo(10L);
        then(teamMemberRepository).should().save(argThat(m -> m.getRole() == TeamMemberRole.MEMBER));
    }

    @Test
    void 존재하지_않는_초대_링크_토큰은_거부한다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findByInviteLinkToken("unknown-token")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinTeamByInviteLink("1", new JoinByInviteLinkRequest("unknown-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않거나 만료된 초대 링크입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 만료된_초대_링크_토큰은_거부한다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        team.updateInviteLink("expired-token", java.time.LocalDateTime.now().minusSeconds(1));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findByInviteLinkToken("expired-token")).willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.joinTeamByInviteLink("1", new JoinByInviteLinkRequest("expired-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않거나 만료된 초대 링크입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 초대_링크로_이미_참여한_팀에는_다시_참여할_수_없다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        team.updateInviteLink("valid-token", java.time.LocalDateTime.now().plusDays(3));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findByInviteLinkToken("valid-token")).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);

        assertThatThrownBy(() -> teamService.joinTeamByInviteLink("1", new JoinByInviteLinkRequest("valid-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 참여한 팀입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 팀장이_이메일로_팀원을_초대한다() {
        setupInviteLinkProperties();
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        InviteTeamResponse response = teamService.inviteTeamMembers(
                "1",
                10L,
                new InviteTeamRequest(List.of("Member@Example.com ", "member@example.com", "second@example.com"))
        );

        assertThat(response.teamId()).isEqualTo(10L);
        assertThat(response.inviteCode()).isEqualTo("ABCD1234");
        assertThat(response.inviteLink()).isEqualTo("https://todo.example.com/teams/join?code=ABCD1234");
        assertThat(response.sentCount()).isEqualTo(2);
        assertThat(response.emails()).containsExactly("member@example.com", "second@example.com");
        then(teamInviteMailService).should().sendInvitations(
                eq(team),
                eq("https://todo.example.com/teams/join?code=ABCD1234"),
                eq(List.of("member@example.com", "second@example.com"))
        );
    }

    @Test
    void 팀원도_이메일로_팀원을_초대한다() {
        setupInviteLinkProperties();
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        InviteTeamResponse response = teamService.inviteTeamMembers(
                "1",
                10L,
                new InviteTeamRequest(List.of("member@example.com"))
        );

        assertThat(response.sentCount()).isEqualTo(1);
        assertThat(response.emails()).containsExactly("member@example.com");
        then(teamInviteMailService).should().sendInvitations(
                eq(team),
                eq("https://todo.example.com/teams/join?code=ABCD1234"),
                eq(List.of("member@example.com"))
        );
    }

    @Test
    void 이메일_초대는_한번에_20명까지만_허용한다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);
        List<String> emails = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "2" + i + "@example.com")
                .toList();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> teamService.inviteTeamMembers("1", 10L, new InviteTeamRequest(emails)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("한 번에 최대 20명까지 초대할 수 있습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 이메일_초대_메일_발송_실패시_예외를_전달한다() {
        setupInviteLinkProperties();
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);
        BusinessException mailException = new BusinessException(
                "초대 메일 발송에 실패했습니다.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        willThrow(mailException).given(teamInviteMailService).sendInvitations(
                eq(team),
                eq("https://todo.example.com/teams/join?code=ABCD1234"),
                eq(List.of("member@example.com"))
        );

        assertThatThrownBy(() -> teamService.inviteTeamMembers(
                "1",
                10L,
                new InviteTeamRequest(List.of("member@example.com"))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("초대 메일 발송에 실패했습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void 팀_나가기_성공() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);
        User remainingUser = User.create("2", "pw", "남은팀원", null);
        ReflectionTestUtils.setField(remainingUser, "id", 2L);
        TeamMember remaining = TeamMember.create(team, remainingUser, TeamMemberRole.LEADER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of(remaining));
        NotificationMessage message = new NotificationMessage(NotificationType.TEAM_MEMBER_LEFT, "title", "content");
        given(notificationMessageFactory.teamMemberLeft()).willReturn(message);

        teamService.leaveTeam("1", 10L);

        then(todoWorkItemLifecycleService).should().handleTeamDeparture(10L, user);
        then(teamMemberRepository).should().delete(member);
        then(notificationService).should().sendAll(List.of(remainingUser), user, message, 10L);
        then(eventPublisher).should().publishEvent(new TeamMembershipRevokedEvent(1L));
    }

    @Test
    void 리더가_팀을_나가면_다음_멤버에게_권한을_넘기고_리더_변경_알림을_보낸다() {
        User leader = User.create("1", "encodedPwd", "팀장", null);
        ReflectionTestUtils.setField(leader, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember leaderMember = TeamMember.create(team, leader, TeamMemberRole.LEADER);
        User nextUser = User.create("2", "pw", "다음팀장", null);
        ReflectionTestUtils.setField(nextUser, "id", 2L);
        TeamMember next = TeamMember.create(team, nextUser, TeamMemberRole.MEMBER);

        given(userRepository.findById(1L)).willReturn(Optional.of(leader));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(leaderMember));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of(next));
        NotificationMessage leftMessage = new NotificationMessage(NotificationType.TEAM_MEMBER_LEFT, "title", "content");
        NotificationMessage leaderChangedMessage = new NotificationMessage(NotificationType.TEAM_LEADER_CHANGED, "title", "content");
        given(notificationMessageFactory.teamMemberLeft()).willReturn(leftMessage);
        given(notificationMessageFactory.teamLeaderChanged()).willReturn(leaderChangedMessage);

        teamService.leaveTeam("1", 10L);

        assertThat(next.getRole()).isEqualTo(TeamMemberRole.LEADER);
        then(notificationService).should().send(nextUser, null, leaderChangedMessage, 10L);
        then(notificationService).should().sendAll(List.of(nextUser), leader, leftMessage, 10L);
        then(teamMemberRepository).should().delete(leaderMember);
    }

    @Test
    void 팀장_강퇴_시_대상_멤버의_진행중_WorkItem을_정리한_후_멤버를_삭제한다() {
        User leader = User.create("1", "encodedPwd", "팀장", null);
        ReflectionTestUtils.setField(leader, "id", 1L);
        User targetUser = User.create("2", "encodedPwd", "팀원", null);
        ReflectionTestUtils.setField(targetUser, "id", 2L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember leaderMember = TeamMember.create(team, leader, TeamMemberRole.LEADER);
        TeamMember targetMember = TeamMember.create(team, targetUser, TeamMemberRole.MEMBER);

        given(userRepository.findById(1L)).willReturn(Optional.of(leader));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(leaderMember));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 2L)).willReturn(Optional.of(targetMember));
        NotificationMessage message = new NotificationMessage(NotificationType.TEAM_MEMBER_REMOVED, "title", "content");
        given(notificationMessageFactory.teamMemberRemoved()).willReturn(message);

        teamService.removeMember("1", 10L, 2L);

        var inOrder = inOrder(todoWorkItemLifecycleService, teamMemberRepository);
        inOrder.verify(todoWorkItemLifecycleService).handleTeamDeparture(10L, targetUser);
        inOrder.verify(teamMemberRepository).delete(targetMember);
        then(notificationService).should().send(targetUser, leader, message, 10L);
        then(eventPublisher).should().publishEvent(new TeamMembershipRevokedEvent(2L));
    }

    @Test
    void 팀_나가기_성공_마지막_리더면_팀데이터까지_삭제한다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(todoRepository.findIdsByTeamId(10L)).willReturn(List.of(100L));
        given(todoWorkItemRepository.findIdsByTodoIdIn(List.of(100L))).willReturn(List.of(1000L));

        teamService.leaveTeam("1", 10L);

        var inOrder = inOrder(todoReactionRepository, todoWorkItemRepository, todoRepository,
                teamChatReadStatusRepository, teamChatMessageRepository, teamMemberRepository, teamRepository);
        inOrder.verify(todoReactionRepository).deleteByTodoWorkItemIdIn(List.of(1000L));
        inOrder.verify(todoWorkItemRepository).deleteByTodoIdIn(List.of(100L));
        inOrder.verify(todoRepository).deleteByIdIn(List.of(100L));
        inOrder.verify(teamChatReadStatusRepository).deleteByTeamId(10L);
        inOrder.verify(teamChatMessageRepository).deleteByTeamId(10L);
        inOrder.verify(teamMemberRepository).deleteByTeamId(10L);
        inOrder.verify(teamRepository).deleteById(10L);
    }

    @Test
    void 팀_나가기_성공_마지막_리더이고_투두가_없으면_팀데이터만_삭제한다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(todoRepository.findIdsByTeamId(10L)).willReturn(List.of());

        teamService.leaveTeam("1", 10L);

        var inOrder = inOrder(teamChatReadStatusRepository, teamChatMessageRepository, teamMemberRepository, teamRepository);
        inOrder.verify(teamChatReadStatusRepository).deleteByTeamId(10L);
        inOrder.verify(teamChatMessageRepository).deleteByTeamId(10L);
        inOrder.verify(teamMemberRepository).deleteByTeamId(10L);
        inOrder.verify(teamRepository).deleteById(10L);
        verify(todoWorkItemRepository, never()).findIdsByTodoIdIn(anyList());
        verify(todoReactionRepository, never()).deleteByTodoWorkItemIdIn(anyList());
        verify(todoWorkItemRepository, never()).deleteByTodoIdIn(anyList());
        verify(todoRepository, never()).deleteByIdIn(anyList());
    }

    @Test
    void 팀_나가기_성공_마지막_리더이고_WorkItem이_없으면_리액션_삭제를_건너뛴다() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(todoRepository.findIdsByTeamId(10L)).willReturn(List.of(100L));
        given(todoWorkItemRepository.findIdsByTodoIdIn(List.of(100L))).willReturn(List.of());

        teamService.leaveTeam("1", 10L);

        var inOrder = inOrder(todoWorkItemRepository, todoRepository,
                teamChatReadStatusRepository, teamChatMessageRepository, teamMemberRepository, teamRepository);
        inOrder.verify(todoWorkItemRepository).deleteByTodoIdIn(List.of(100L));
        inOrder.verify(todoRepository).deleteByIdIn(List.of(100L));
        inOrder.verify(teamChatReadStatusRepository).deleteByTeamId(10L);
        inOrder.verify(teamChatMessageRepository).deleteByTeamId(10L);
        inOrder.verify(teamMemberRepository).deleteByTeamId(10L);
        inOrder.verify(teamRepository).deleteById(10L);
        verify(todoReactionRepository, never()).deleteByTodoWorkItemIdIn(anyList());
    }

    @Test
    void 팀전체_삭제시_팀이미지와_모든_인증사진을_파일삭제_outbox에_적재한다() {
        Team team = Team.create("스터디 팀", "teams/10/team.png", "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(todoRepository.findIdsByTeamId(10L)).willReturn(List.of(100L, 101L));
        given(todoWorkItemRepository.findProofImageKeysByTodoIdIn(List.of(100L, 101L)))
                .willReturn(List.of("proofs/1/a.png", "proofs/2/b.png"));
        given(todoWorkItemRepository.findProofThumbnailKeysByTodoIdIn(List.of(100L, 101L)))
                .willReturn(List.of("proofs/1/thumbs/a.jpg", "proofs/2/thumbs/b.jpg"));
        given(todoWorkItemRepository.findIdsByTodoIdIn(List.of(100L, 101L))).willReturn(List.of());

        teamService.deleteTeamWithAllData(10L);

        verify(fileDeletionOutboxService).enqueueAll(List.of(
                "teams/10/team.png",
                "proofs/1/a.png",
                "proofs/2/b.png",
                "proofs/1/thumbs/a.jpg",
                "proofs/2/thumbs/b.jpg"
        ));
    }

    @Test
    void 팀전체_삭제시_팀이_없으면_거부한다() {
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.deleteTeamWithAllData(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 팀입니다");

        then(fileDeletionOutboxService).shouldHaveNoInteractions();
    }

    @Test
    void 팀_나가기_실패_존재하지_않는_사용자() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.leaveTeam("999", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_나가기_실패_소속된_팀이_아님() {
        User user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.leaveTeam("1", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소속된 팀이 아닙니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_달성_통계_조회_성공() {
        User user = User.create("1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);
        ReflectionTestUtils.setField(team, "successCount", 2);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(true);

        var response = teamService.getTeamAchievement(1L, "1");

        assertThat(response.teamId()).isEqualTo(1L);
        assertThat(response.successCount()).isEqualTo(2);
    }

    @Test
    void 팀_달성_통계_조회_실패_존재하지_않는_팀() {
        User user = User.create("1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamAchievement(99L, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 팀입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_달성_통계_조회_실패_팀에_속하지_않은_사용자() {
        User user = User.create("1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamService.getTeamAchievement(1L, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void setupInviteLinkProperties() {
        ReflectionTestUtils.setField(teamService, "frontendBaseUrl", "https://todo.example.com/");
        ReflectionTestUtils.setField(teamService, "teamInvitePath", "/teams/join");
    }

    private void setupTeamInviteLinkProperties() {
        ReflectionTestUtils.setField(teamService, "apiServerUrl", "https://backend.example.com/");
        ReflectionTestUtils.setField(teamService, "teamInviteLinkPath", "/invite");
    }
}
