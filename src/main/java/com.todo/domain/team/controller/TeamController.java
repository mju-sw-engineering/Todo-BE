package com.todo.domain.team.controller;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.service.TeamService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateTeamResponse>> createTeam(
            @Valid @RequestPart("request") CreateTeamRequest request,
            @RequestPart(value = "teamImage", required = false) MultipartFile teamImage,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        CreateTeamResponse response = teamService.createTeam(loginId, request, teamImage);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "팀 생성이 완료됐습니다"));
    }
}
