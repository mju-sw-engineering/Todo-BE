package com.todo.domain.team.service;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.argThat;

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

    @Test
    void 팀_생성_성공_이미지없음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);

        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.persistTeam(user, "우리팀", null);

        assertThat(response.teamName()).isEqualTo("우리팀");
        assertThat(response.teamImage()).isNull();
        assertThat(response.inviteCode()).hasSize(8);
        assertThat(response.consecutiveTodoCount()).isZero();
    }

    @Test
    void 팀_생성_성공_이미지있음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);

        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.persistTeam(user, "우리팀", "/uploads/teams/uuid_team.jpg");

        assertThat(response.teamImage()).isEqualTo("/uploads/teams/uuid_team.jpg");
    }

    @Test
    void 팀_생성_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.createTeam("unknown", new CreateTeamRequest("팀"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용자를 찾을 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 팀_생성_실패_허용되지않는_이미지_확장자() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        MockMultipartFile gif = new MockMultipartFile("teamImage", "team.gif", "image/gif", "img".getBytes());

        assertThatThrownBy(() -> teamService.createTeam("user1", new CreateTeamRequest("팀"), gif))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지원하지 않는 이미지 형식입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 팀_생성_시_팀장으로_등록된다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);

        given(teamRepository.existsByInviteCode(anyString())).willReturn(false);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        teamService.persistTeam(user, "우리팀", null);

        then(teamMemberRepository).should().save(argThat(member -> member.getRole() == TeamMemberRole.LEADER));
    }

    @Test
    void 초대코드_중복시_재시도하여_고유코드_생성() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);

        given(teamRepository.existsByInviteCode(anyString()))
                .willReturn(true)   // 1회 중복
                .willReturn(false); // 2회 성공
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));
        given(teamMemberRepository.save(any(TeamMember.class))).willAnswer(inv -> inv.getArgument(0));

        CreateTeamResponse response = teamService.persistTeam(user, "우리팀", null);

        assertThat(response.inviteCode()).hasSize(8);
    }
}
