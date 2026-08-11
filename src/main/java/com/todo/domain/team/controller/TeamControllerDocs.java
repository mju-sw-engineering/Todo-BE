package com.todo.domain.team.controller;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.InviteTeamRequest;
import com.todo.domain.team.dto.request.JoinByInviteLinkRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.InviteLinkResponse;
import com.todo.domain.team.dto.response.InviteTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamAchievementResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamHiveResponse;
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

    @Operation(summary = "팀 벌집 성장 조회",
            description = "팀이 함께 모은 누적 기록 수와 벌집 레벨(1~4)을 조회합니다. "
                    + "기록은 피드와 같은 활동 기준(투두 생성·제출·체크인)이며 (팀원, 날짜, 투두) 단위로 중복 없이 셉니다. "
                    + "레벨 문턱값은 0 / 30 / 100 / 300입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TeamHiveResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 접근 권한 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<TeamHiveResponse>> getTeamHive(Long teamId, Authentication authentication);

    @Operation(summary = "팀 달성 통계 조회", description = "팀의 누적 성공 투두 수와 연속 완료 횟수를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "달성 통계 조회 성공",
                    content = @Content(schema = @Schema(implementation = TeamAchievementResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 접근 권한 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<TeamAchievementResponse>> getTeamAchievement(Long teamId, Authentication authentication);

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

    @Operation(
            summary = "팀 초대 링크 조회/발급",
            description = "공유용 초대 링크를 반환합니다. 유효한 링크가 있으면 그대로 반환하고, 없거나 만료됐으면 "
                    + "새로 발급합니다(7일 유효). 팀 멤버라면 누구나 호출할 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회/발급 성공",
                    content = @Content(schema = @Schema(implementation = InviteLinkResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 접근 권한 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<InviteLinkResponse>> getOrCreateInviteLink(Long teamId, Authentication authentication);

    @Operation(summary = "초대 링크로 팀 참여", description = "공유받은 초대 링크의 token으로 팀에 참여합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "팀 참여 성공",
                    content = @Content(schema = @Schema(implementation = JoinTeamResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "토큰 미입력",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유효하지 않거나 만료된 초대 링크",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 참여한 팀",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<JoinTeamResponse>> joinTeamByInviteLink(JoinByInviteLinkRequest request, Authentication authentication);

    @Operation(
            summary = "팀 이메일 초대",
            description = "팀원이 입력한 이메일 주소로 팀 참여 링크와 초대 코드를 발송합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "팀 초대 메일 발송 성공",
                    content = @Content(schema = @Schema(implementation = InviteTeamResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이메일 입력값 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 접근 권한 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "초대 메일 발송 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<InviteTeamResponse>> inviteTeamMembers(
            Long teamId,
            InviteTeamRequest request,
            Authentication authentication
    );

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

    @Operation(summary = "팀원 강퇴", description = "팀장이 특정 팀원을 팀에서 강퇴합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "강퇴 성공",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "자신을 강퇴 시도",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀장 권한 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 또는 멤버 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> removeMember(Long teamId, Long targetUserId, Authentication authentication);

    @Operation(summary = "팀 나가기", description = "로그인한 사용자가 소속된 팀에서 나갑니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "팀 나가기 성공",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "소속된 팀이 아님",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> leaveTeam(Long teamId, Authentication authentication);
}
