package com.todo.domain.team.service;

import com.todo.domain.chat.repository.ChatMessageRepository;
import com.todo.domain.evaluation.repository.DailyEvaluationRepository;
import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.InviteTeamRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.request.UpdateTeamPersonaRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.InviteTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.dto.response.UpdateTeamPersonaResponse;
import com.todo.domain.team.entity.AiPersona;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
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
    private TodoParticipantRepository todoParticipantRepository;
    @Mock
    private TodoReactionRepository todoReactionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private DailyEvaluationRepository dailyEvaluationRepository;

    @Test
    void 팀_생성_성공_이미지없음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.createTeam("user1", new CreateTeamRequest("우리팀", null));

        assertThat(response.teamName()).isEqualTo("우리팀");
        assertThat(response.teamImage()).isNull();
        assertThat(response.inviteCode()).hasSize(8);
        assertThat(response.aiPersona()).isEqualTo(AiPersona.ANGEL);
        assertThat(response.consecutiveTodoCount()).isZero();
    }

    @Test
    void 팀_생성_시_aiPersona_저장() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.createTeam(
                "user1",
                new CreateTeamRequest("우리팀", null, AiPersona.DEVIL)
        );

        assertThat(response.aiPersona()).isEqualTo(AiPersona.DEVIL);
        then(teamRepository).should().save(argThat(team -> team.getAiPersona() == AiPersona.DEVIL));
    }

    @Test
    void 팀_생성_성공_이미지있음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        String imageKey = "teams/temp/uuid.jpg";
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(fileService.resolveImageUrl(imageKey)).willReturn("https://minio.example.com/image.jpg");

        CreateTeamResponse response = teamService.createTeam("user1", new CreateTeamRequest("우리팀", imageKey));

        assertThat(response.teamImage()).isEqualTo("https://minio.example.com/image.jpg");
    }

    @Test
    void 팀_생성_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.createTeam("unknown", new CreateTeamRequest("팀", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용자를 찾을 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_생성_시_팀장으로_등록된다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        teamService.createTeam("user1", new CreateTeamRequest("우리팀", null));

        then(teamMemberRepository).should().save(argThat(member -> member.getRole() == TeamMemberRole.LEADER));
    }

    @Test
    void 초대코드_중복시_재시도하여_고유코드_생성() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.existsByInviteCode(anyString()))
                .willReturn(true)   // 1회 중복
                .willReturn(false); // 2회 성공
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.createTeam("user1", new CreateTeamRequest("우리팀", null));

        assertThat(response.inviteCode()).hasSize(8);
    }

    @Test
    void 내_팀_목록_조회_성공_팀없음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());

        TeamListResponse response = teamService.getMyTeams("user1");

        assertThat(response.teams()).isEmpty();
    }

    @Test
    void 내_팀_목록_조회_성공_팀있음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team studyTeam = Team.create("스터디 팀", "https://example.com/team1.png", "ABCDEFGH");
        ReflectionTestUtils.setField(studyTeam, "id", 10L);
        Team exerciseTeam = Team.create("운동 팀", null, "IJKLMNOP");
        ReflectionTestUtils.setField(exerciseTeam, "id", 20L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of(studyTeam, exerciseTeam));
        given(fileService.resolveImageUrl("https://example.com/team1.png")).willReturn("https://example.com/team1.png");

        TeamListResponse response = teamService.getMyTeams("user1");

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
        User user = User.create("user1", "encodedPwd", "홍길동", "https://example.com/profile1.png");
        ReflectionTestUtils.setField(user, "id", 1L);
        User member2 = User.create("user2", "encodedPwd", "김철수", null);
        ReflectionTestUtils.setField(member2, "id", 2L);

        Team team = Team.create("스터디 팀", "https://example.com/team.png", "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);

        TeamMember leader = TeamMember.create(team, user, TeamMemberRole.LEADER);
        TeamMember memberEntry = TeamMember.create(team, member2, TeamMemberRole.MEMBER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(true);
        given(teamMemberRepository.findByTeamIdWithUser(1L)).willReturn(List.of(leader, memberEntry));
        given(fileService.resolveImageUrl(null)).willReturn(null);
        given(fileService.resolveImageUrl("https://example.com/team.png")).willReturn("https://example.com/team.png");
        given(fileService.resolveImageUrl("https://example.com/profile1.png")).willReturn("https://example.com/profile1.png");

        TeamDetailResponse response = teamService.getTeamDetail(1L, "user1");

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
        User user = User.create("user1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamDetail(99L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 팀입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_상세_조회_실패_팀에_속하지_않은_사용자() {
        User user = User.create("user1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 팀_상세_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 내_팀_목록_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getMyTeams("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_참여_성공() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findByInviteCode("ABCD1234")).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(false);
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        JoinTeamResponse response = teamService.joinTeam("user1", new JoinTeamRequest("ABCD1234"));

        assertThat(response.teamId()).isEqualTo(10L);
        then(teamMemberRepository).should().save(argThat(m -> m.getRole() == TeamMemberRole.MEMBER));
    }

    @Test
    void 팀_참여_실패_유효하지_않은_초대_코드() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findByInviteCode("INVALID")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinTeam("user1", new JoinTeamRequest("INVALID")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않은 초대 코드입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_참여_실패_이미_참여한_팀() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findByInviteCode("ABCD1234")).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);

        assertThatThrownBy(() -> teamService.joinTeam("user1", new JoinTeamRequest("ABCD1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 참여한 팀입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 팀_참여_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinTeam("unknown", new JoinTeamRequest("ABCD1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀장이_이메일로_팀원을_초대한다() {
        setupInviteLinkProperties();
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        InviteTeamResponse response = teamService.inviteTeamMembers(
                "user1",
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
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        InviteTeamResponse response = teamService.inviteTeamMembers(
                "user1",
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
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);
        List<String> emails = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "member" + i + "@example.com")
                .toList();

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> teamService.inviteTeamMembers("user1", 10L, new InviteTeamRequest(emails)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("한 번에 최대 20명까지 초대할 수 있습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 이메일_초대_메일_발송_실패시_예외를_전달한다() {
        setupInviteLinkProperties();
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);
        BusinessException mailException = new BusinessException(
                "초대 메일 발송에 실패했습니다.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        willThrow(mailException).given(teamInviteMailService).sendInvitations(
                eq(team),
                eq("https://todo.example.com/teams/join?code=ABCD1234"),
                eq(List.of("member@example.com"))
        );

        assertThatThrownBy(() -> teamService.inviteTeamMembers(
                "user1",
                10L,
                new InviteTeamRequest(List.of("member@example.com"))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("초대 메일 발송에 실패했습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void 팀장이_aiPersona를_변경한다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234", AiPersona.ANGEL);
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        UpdateTeamPersonaResponse response = teamService.updateTeamPersona(
                "user1",
                10L,
                new UpdateTeamPersonaRequest(AiPersona.DEVIL)
        );

        assertThat(response.teamId()).isEqualTo(10L);
        assertThat(response.aiPersona()).isEqualTo(AiPersona.DEVIL);
        assertThat(team.getAiPersona()).isEqualTo(AiPersona.DEVIL);
    }

    @Test
    void 팀원이_aiPersona를_변경하면_실패한다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234", AiPersona.ANGEL);
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> teamService.updateTeamPersona(
                "user1",
                10L,
                new UpdateTeamPersonaRequest(AiPersona.DEVIL)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀 설정을 변경할 권한이 없습니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 팀_나가기_성공() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.MEMBER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));

        teamService.leaveTeam("user1", 10L);

        then(teamMemberRepository).should().delete(member);
    }

    @Test
    void 팀_나가기_성공_마지막_리더면_팀데이터까지_삭제한다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(todoRepository.findIdsByTeamId(10L)).willReturn(List.of(100L));
        given(todoParticipantRepository.findIdsByTodoIdIn(List.of(100L))).willReturn(List.of(1000L));

        teamService.leaveTeam("user1", 10L);

        var inOrder = inOrder(todoReactionRepository, chatMessageRepository, todoParticipantRepository,
                todoRepository, dailyEvaluationRepository, teamMemberRepository, teamRepository);
        inOrder.verify(todoReactionRepository).deleteByTodoParticipantIdIn(List.of(1000L));
        inOrder.verify(chatMessageRepository).deleteByTodoIdIn(List.of(100L));
        inOrder.verify(todoParticipantRepository).deleteByTodoIdIn(List.of(100L));
        inOrder.verify(todoRepository).deleteByIdIn(List.of(100L));
        inOrder.verify(dailyEvaluationRepository).deleteByTeamId(10L);
        inOrder.verify(teamMemberRepository).deleteByTeamId(10L);
        inOrder.verify(teamRepository).deleteById(10L);
    }

    @Test
    void 팀_나가기_성공_마지막_리더이고_투두가_없으면_팀데이터만_삭제한다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(todoRepository.findIdsByTeamId(10L)).willReturn(List.of());

        teamService.leaveTeam("user1", 10L);

        var inOrder = inOrder(dailyEvaluationRepository, teamMemberRepository, teamRepository);
        inOrder.verify(dailyEvaluationRepository).deleteByTeamId(10L);
        inOrder.verify(teamMemberRepository).deleteByTeamId(10L);
        inOrder.verify(teamRepository).deleteById(10L);
        verify(todoParticipantRepository, never()).findIdsByTodoIdIn(anyList());
        verify(todoReactionRepository, never()).deleteByTodoParticipantIdIn(anyList());
        verify(chatMessageRepository, never()).deleteByTodoIdIn(anyList());
        verify(todoParticipantRepository, never()).deleteByTodoIdIn(anyList());
        verify(todoRepository, never()).deleteByIdIn(anyList());
    }

    @Test
    void 팀_나가기_성공_마지막_리더이고_투두_참가자가_없으면_리액션_삭제를_건너뛴다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember member = TeamMember.create(team, user, TeamMemberRole.LEADER);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(todoRepository.findIdsByTeamId(10L)).willReturn(List.of(100L));
        given(todoParticipantRepository.findIdsByTodoIdIn(List.of(100L))).willReturn(List.of());

        teamService.leaveTeam("user1", 10L);

        var inOrder = inOrder(chatMessageRepository, todoParticipantRepository, todoRepository,
                dailyEvaluationRepository, teamMemberRepository, teamRepository);
        inOrder.verify(chatMessageRepository).deleteByTodoIdIn(List.of(100L));
        inOrder.verify(todoParticipantRepository).deleteByTodoIdIn(List.of(100L));
        inOrder.verify(todoRepository).deleteByIdIn(List.of(100L));
        inOrder.verify(dailyEvaluationRepository).deleteByTeamId(10L);
        inOrder.verify(teamMemberRepository).deleteByTeamId(10L);
        inOrder.verify(teamRepository).deleteById(10L);
        verify(todoReactionRepository, never()).deleteByTodoParticipantIdIn(anyList());
    }

    @Test
    void 팀_나가기_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.leaveTeam("unknown", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_나가기_실패_소속된_팀이_아님() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findByTeamIdAndUserId(10L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.leaveTeam("user1", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소속된 팀이 아닙니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_달성_통계_조회_성공() {
        User user = User.create("user1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);
        team.incrementSuccessCount();
        team.incrementSuccessCount();

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(true);

        var response = teamService.getTeamAchievement(1L, "user1");

        assertThat(response.teamId()).isEqualTo(1L);
        assertThat(response.successCount()).isEqualTo(2);
    }

    @Test
    void 팀_달성_통계_조회_실패_존재하지_않는_팀() {
        User user = User.create("user1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamAchievement(99L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 팀입니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 팀_달성_통계_조회_실패_팀에_속하지_않은_사용자() {
        User user = User.create("user1", "encodedPwd", "홍길동", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamService.getTeamAchievement(1L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void setupInviteLinkProperties() {
        ReflectionTestUtils.setField(teamService, "frontendBaseUrl", "https://todo.example.com/");
        ReflectionTestUtils.setField(teamService, "teamInvitePath", "/teams/join");
    }
}
