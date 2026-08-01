package com.todo.domain.user.controller;

import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "User", description = "사용자 API")
public interface UserControllerDocs {

    @Operation(summary = "마이페이지 조회", description = "로그인한 사용자의 마이페이지 정보를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "마이페이지 조회 성공",
                    content = @Content(schema = @Schema(implementation = MyPageResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(Authentication authentication);

    @Operation(summary = "내 닉네임 수정", description = "로그인한 사용자의 닉네임을 수정합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "닉네임 수정 성공",
                    content = @Content(schema = @Schema(implementation = MyPageResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<MyPageResponse>> updateNickname(
            UpdateNicknameRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    재인증 후 계정을 삭제합니다.

                    먼저 POST /api/auth/reauth 로 비밀번호를 확인해 재인증 토큰을 발급받아야 합니다.
                    토큰은 1회용이며 5분간 유효합니다.

                    개인정보(계정, 알림, 읽음 상태, 동의 이력, 이메일 인증 기록)는 실제로 삭제하고,
                    팀 공동 기록은 삭제하지 않고 작성자만 익명화합니다.
                    탈퇴자가 만든 투두와 완료된 참가 기록은 남으며 작성자가 '탈퇴한 사용자'로 표시됩니다.
                    진행 중이던 배정은 배정 목록에서 제거되고 해당 투두 상태가 재평가됩니다.
                    복구 유예기간은 없습니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "재인증 토큰 누락",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증 실패 또는 재인증 토큰이 유효하지 않음(만료·사용됨·용도 불일치)",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "권한 이양 중 오류",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> deleteUser(
            DeleteUserRequest request,
            Authentication authentication
    );

}
