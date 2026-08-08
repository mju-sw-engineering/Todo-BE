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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController implements TeamControllerDocs {

    private final TeamService teamService;
    private final TeamHiveService teamHiveService;

    @GetMapping("/{teamId}/hive")
    public ResponseEntity<ApiResponse<TeamHiveResponse>> getTeamHive(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(teamHiveService.getTeamHive(teamId, loginId)));
    }

    @GetMapping("/{teamId}/achievements")
    public ResponseEntity<ApiResponse<TeamAchievementResponse>> getTeamAchievement(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        TeamAchievementResponse response = teamService.getTeamAchievement(teamId, loginId);
        return ResponseEntity.ok(ApiResponse.success(response, "팀 달성률을 조회했습니다"));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> getTeamDetail(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        TeamDetailResponse response = teamService.getTeamDetail(teamId, loginId);
        return ResponseEntity.ok(ApiResponse.success(response, "팀 정보를 조회했습니다"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TeamListResponse>> getMyTeams(Authentication authentication) {
        String loginId = authentication.getName();
        TeamListResponse response = teamService.getMyTeams(loginId);
        return ResponseEntity.ok(ApiResponse.success(response, "팀 목록을 조회했습니다"));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinTeamResponse>> joinTeam(
            @Valid @RequestBody JoinTeamRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        JoinTeamResponse response = teamService.joinTeam(loginId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "팀 참여가 완료되었습니다"));
    }

    @PostMapping("/{teamId}/invitations")
    public ResponseEntity<ApiResponse<InviteTeamResponse>> inviteTeamMembers(
            @PathVariable Long teamId,
            @Valid @RequestBody InviteTeamRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        InviteTeamResponse response = teamService.inviteTeamMembers(loginId, teamId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "팀 초대 메일이 발송되었습니다"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateTeamResponse>> createTeam(
            @Valid @RequestBody CreateTeamRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        CreateTeamResponse response = teamService.createTeam(loginId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "팀 생성이 완료됐습니다"));
    }

    @DeleteMapping("/{teamId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long teamId,
            @PathVariable Long targetUserId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        teamService.removeMember(loginId, teamId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "팀원이 강퇴되었습니다"));
    }

    @DeleteMapping("/{teamId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveTeam(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        teamService.leaveTeam(loginId, teamId);
        return ResponseEntity.ok(ApiResponse.success(null, "그룹에서 나왔습니다"));
    }
}
