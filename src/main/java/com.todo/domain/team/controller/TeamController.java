package com.todo.domain.team.controller;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.request.UpdateTeamPersonaRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.dto.response.UpdateTeamPersonaResponse;
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

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamDetailResponse>> getTeamDetail(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        TeamDetailResponse response = teamService.getTeamDetail(teamId, loginId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TeamListResponse>> getMyTeams(Authentication authentication) {
        String loginId = authentication.getName();
        TeamListResponse response = teamService.getMyTeams(loginId);
        return ResponseEntity.ok(ApiResponse.success(response));
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

    @PatchMapping("/{teamId}/persona")
    public ResponseEntity<ApiResponse<UpdateTeamPersonaResponse>> updateTeamPersona(
            @PathVariable Long teamId,
            @Valid @RequestBody UpdateTeamPersonaRequest request,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        UpdateTeamPersonaResponse response = teamService.updateTeamPersona(loginId, teamId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "AI 평가 페르소나가 변경되었습니다"));
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
