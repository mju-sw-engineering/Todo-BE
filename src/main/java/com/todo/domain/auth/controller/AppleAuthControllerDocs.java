package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.AppleSetupResponse;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Apple 인증", description = "Sign in with Apple 로그인/가입 API")
public interface AppleAuthControllerDocs {

    @Operation(summary = "Apple 로그인",
            description = "Apple identity token을 검증해 기존 유저면 토큰을 발급하고, " +
                    "신규 유저면 닉네임 입력용 setup token을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "기존 유저 — 로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202",
                    description = "신규 유저 — 닉네임 입력 필요 (setup token 반환)",
                    content = @Content(schema = @Schema(implementation = AppleSetupResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "identity token 검증 실패")
    })
    ResponseEntity<ApiResponse<?>> appleLogin(AppleLoginRequest request);

    @Operation(summary = "Apple 가입 완료",
            description = "setup token과 닉네임을 받아 신규 Apple 유저를 생성하고 토큰을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "가입 완료 — 로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "setup token 만료 또는 위변조")
    })
    ResponseEntity<ApiResponse<LoginResponse>> appleComplete(AppleCompleteRequest request);
}
