package com.todo.domain.team.controller;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
@Tag(name = "Team", description = "팀 API")
public interface TeamControllerDocs {

    @Operation(summary = "팀 상세 조회", description = "특정 팀의 상세 정보와 팀원 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "팀 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = TeamDetailResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 접근 권한 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<TeamDetailResponse>> getTeamDetail(Long teamId, Authentication authentication);

    @Operation(summary = "내 팀 목록 조회", description = "로그인한 사용자가 참여 중인 팀 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "팀 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = TeamListResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<TeamListResponse>> getMyTeams(Authentication authentication);

    @Operation(summary = "팀 참여", description = "초대 코드를 입력하여 팀에 참여합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "팀 참여 성공",
                    content = @Content(schema = @Schema(implementation = JoinTeamResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "초대 코드 미입력",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유효하지 않은 초대 코드",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 참여한 팀",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<JoinTeamResponse>> joinTeam(JoinTeamRequest request, Authentication authentication);

    @Operation(summary = "팀 생성", description = "새로운 팀을 생성하고 요청자를 팀장으로 등록합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "팀 생성 성공",
                    content = @Content(schema = @Schema(implementation = CreateTeamResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<CreateTeamResponse>> createTeam(
            CreateTeamRequest request,
            Authentication authentication
    );
}
