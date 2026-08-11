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
                    "신규 유저면 추가 정보 입력용 setup token을 반환합니다. " +
                    "nonce는 원본 값을 보내야 하며, 서버가 SHA-256 hex로 해싱해 토큰의 nonce 클레임과 대조합니다. " +
                    "즉 Apple에는 해시를, 이 API에는 원본을 보냅니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "기존 유저 — 로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202",
                    description = "신규 유저 — 추가 정보 입력 필요 (setup token 반환, 5분 유효)",
                    content = @Content(schema = @Schema(implementation = AppleSetupResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "identity token 검증 실패 (서명·issuer·audience·nonce 불일치)")
    })
    ResponseEntity<ApiResponse<?>> appleLogin(AppleLoginRequest request);

    @Operation(summary = "Apple 가입 완료",
            description = "setup token과 닉네임·프로필·약관 동의를 받아 신규 Apple 유저를 생성하고 토큰을 발급합니다. " +
                    "이메일 가입과 동일하게 이용약관·개인정보 동의 이력을 남기므로 두 필수 항목은 true여야 합니다. " +
                    "이메일은 1단계 identity token에서 서버가 추출하므로 요청에 담지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "가입 완료 — 로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "setup token 만료·위변조 또는 필수 약관 미동의"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "이미 가입된 Apple 계정")
    })
    ResponseEntity<ApiResponse<LoginResponse>> appleComplete(AppleCompleteRequest request);
}
