package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.response.SessionResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "Session", description = "로그인 세션(기기) 관리 API")
public interface SessionControllerDocs {

    @Operation(
            summary = "내 활성 세션 목록 조회",
            description = "현재 로그인된 기기(세션) 목록을 최신순으로 반환합니다. "
                    + "클라이언트가 로그인 시 deviceId를 보내지 않았다면 해당 세션의 deviceId는 null입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SessionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions(Authentication authentication, String refreshToken);

    @Operation(
            summary = "세션 개별 로그아웃",
            description = "지정한 세션 하나만 로그아웃합니다. 다른 기기의 세션은 유지됩니다. 본인 소유 세션만 지울 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션이 없거나 본인 소유가 아님",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> revokeSession(Long sessionId, Authentication authentication);
}
