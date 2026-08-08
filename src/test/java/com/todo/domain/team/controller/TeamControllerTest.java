package com.todo.domain.team.controller;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.InviteTeamRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.InviteTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamAchievementResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamHiveResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.service.TeamHiveService;
import com.todo.domain.team.service.TeamService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock
    private TeamService teamService;

    @Mock
    private TeamHiveService teamHiveService;

    @Test
    void 팀_벌집_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);
        TeamHiveResponse serviceResponse = TeamHiveResponse.of(3, 278, 100, 300);
        given(teamHiveService.getTeamHive(1L, "user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TeamHiveResponse>> response = controller.getTeamHive(1L, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_달성_통계_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);
        TeamAchievementResponse serviceResponse = new TeamAchievementResponse(1L, 5);
        given(teamService.getTeamAchievement(1L, "user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TeamAchievementResponse>> response = controller.getTeamAchievement(1L, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getData().successCount()).isEqualTo(5);
    }

    @Test
    void 팀_상세_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);
        TeamDetailResponse serviceResponse = new TeamDetailResponse(
                1L, "팀", null, null, "ABCDEFGH", 0, 0, List.of()
        );
        given(teamService.getTeamDetail(1L, "user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TeamDetailResponse>> response = controller.getTeamDetail(1L, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 내_팀_목록_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);
        TeamListResponse serviceResponse = new TeamListResponse(List.of());
        given(teamService.getMyTeams("user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TeamListResponse>> response = controller.getMyTeams(auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_참여_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);
        JoinTeamRequest request = new JoinTeamRequest("ABCDEFGH");
        JoinTeamResponse serviceResponse = new JoinTeamResponse(1L);
        given(teamService.joinTeam("user1", request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<JoinTeamResponse>> response = controller.joinTeam(request, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("팀 참여가 완료되었습니다");
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_초대_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);
        InviteTeamRequest request = new InviteTeamRequest(List.of("member@example.com"));
        InviteTeamResponse serviceResponse = new InviteTeamResponse(
                1L, "팀", "ABCDEFGH", "http://localhost:3000/teams/join?code=ABCDEFGH", 1, request.emails()
        );
        given(teamService.inviteTeamMembers("user1", 1L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<InviteTeamResponse>> response = controller.inviteTeamMembers(1L, request, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("팀 초대 메일이 발송되었습니다");
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀_생성_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);
        CreateTeamRequest request = new CreateTeamRequest("팀", null, null);
        CreateTeamResponse serviceResponse = new CreateTeamResponse(
                1L, "팀", null, null, "ABCDEFGH", 1L, null
        );
        given(teamService.createTeam("user1", request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<CreateTeamResponse>> response = controller.createTeam(request, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getMessage()).isEqualTo("팀 생성이 완료됐습니다");
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 팀원_강퇴_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);

        ResponseEntity<ApiResponse<Void>> response = controller.removeMember(1L, 2L, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("팀원이 강퇴되었습니다");
        then(teamService).should().removeMember("user1", 1L, 2L);
    }

    @Test
    void 팀_나가기_응답을_반환한다() {
        TeamController controller = new TeamController(teamService, teamHiveService);

        ResponseEntity<ApiResponse<Void>> response = controller.leaveTeam(1L, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("그룹에서 나왔습니다");
        then(teamService).should().leaveTeam("user1", 1L);
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
