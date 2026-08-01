package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.EmailSendRequest;
import com.todo.domain.auth.dto.request.EmailVerifyRequest;
import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.ReauthRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.EmailVerifyResponse;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.ReauthResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Auth", description = "인증 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "이메일 인증 코드 발송 요청",
            description = "6자리 인증 코드 발송 요청을 outbox에 저장하고 비동기로 처리합니다. "
                    + "202 응답은 발송 요청 접수를 의미하며 실제 메일 도착을 보장하지 않습니다. 코드는 3분간 유효합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "인증 코드 발송 요청 접수"),
            @ApiResponse(responseCode = "400", description = "이메일 형식 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "429", description = "인증 코드 재요청 제한",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "500", description = "발송 요청 저장 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<com.todo.global.response.ApiResponse<Void>> sendEmailCode(EmailSendRequest request);

    @Operation(summary = "이메일 인증 코드 확인", description = "발송된 6자리 코드를 확인하고 회원가입에 사용할 인증 토큰을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공 — emailVerificationToken 반환",
                    content = @Content(schema = @Schema(implementation = EmailVerifyResponse.class))),
            @ApiResponse(responseCode = "400", description = "코드 불일치 / 만료 / 인증 요청 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<com.todo.global.response.ApiResponse<EmailVerifyResponse>> verifyEmailCode(EmailVerifyRequest request);

    @Operation(summary = "회원가입", description = "이메일 인증 후 아이디·비밀번호·닉네임으로 계정을 생성합니다. emailVerificationToken 필수.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = SignupResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 오류 또는 중복 아이디",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<com.todo.global.response.ApiResponse<SignupResponse>> signup(SignupRequest request);

    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인하고 JWT 액세스 토큰을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "아이디 또는 비밀번호 오류",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<com.todo.global.response.ApiResponse<LoginResponse>> login(LoginRequest request);

    @Operation(summary = "로그아웃", description = "로그인된 사용자를 로그아웃합니다. 클라이언트에서 토큰을 삭제해야 합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<com.todo.global.response.ApiResponse<Void>> logout();


    @Operation(
            summary = "재인증",
            description = """
                    민감한 작업 직전에 비밀번호를 다시 확인하고 1회용 재인증 토큰을 발급합니다.

                    발급된 토큰은 5분간 유효하며 한 번만 사용할 수 있습니다.
                    purpose에 지정한 작업에만 쓸 수 있고, 같은 용도로 재발급하면 이전 토큰은 무효가 됩니다.
                    토큰 원문은 이 응답에서만 확인할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재인증 성공",
                    content = @Content(schema = @Schema(implementation = ReauthResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "429", description = "비밀번호 확인 시도 초과",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<com.todo.global.response.ApiResponse<ReauthResponse>> reauthenticate(
            ReauthRequest request,
            Authentication authentication
    );
}
