package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.AppleReauthRequest;
import com.todo.domain.auth.dto.request.EmailSendRequest;
import com.todo.domain.auth.dto.request.EmailVerifyRequest;
import com.todo.domain.auth.dto.request.FindIdRequest;
import com.todo.domain.auth.dto.request.FindPasswordRequest;
import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.ReauthRequest;
import com.todo.domain.auth.dto.request.ResetPasswordRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.EmailVerifyResponse;
import com.todo.domain.auth.dto.response.FindIdResponse;
import com.todo.domain.auth.dto.response.FindPasswordResponse;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.dto.response.ReauthResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.service.AccountRecoveryService;
import com.todo.domain.auth.service.AuthService;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.auth.service.EmailVerificationService;
import com.todo.domain.auth.service.SessionService;
import com.todo.global.ratelimit.ClientIpResolver;
import com.todo.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final long REFRESH_COOKIE_MAX_AGE_SECONDS = 14L * 24 * 60 * 60;

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final ReauthService reauthService;
    private final AccountRecoveryService accountRecoveryService;
    private final SessionService sessionService;
    private final ClientIpResolver clientIpResolver;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailCode(
            @Valid @RequestBody EmailSendRequest request,
            HttpServletRequest httpRequest
    ) {
        emailVerificationService.sendCode(request.email(), clientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted()
                .body(ApiResponse.success(null, "인증 코드 발송 요청이 접수되었습니다."));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<EmailVerifyResponse>> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
        String token = emailVerificationService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.success(new EmailVerifyResponse(token), "이메일 인증이 완료되었습니다"));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.signup(request), "회원가입이 완료되었습니다"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResult result = authService.login(request, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.success(new LoginResponse(result.accessToken()), "로그인되었습니다"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        LoginResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.success(new LoginResponse(result.accessToken()), "토큰이 갱신되었습니다"));
    }

    @PostMapping("/reauth")
    public ResponseEntity<ApiResponse<ReauthResponse>> reauthenticate(
            @Valid @RequestBody ReauthRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(reauthService.reauthenticate(userId, request), "재인증이 완료되었습니다"));
    }

    @PostMapping("/reauth/apple")
    public ResponseEntity<ApiResponse<ReauthResponse>> reauthenticateWithApple(
            @Valid @RequestBody AppleReauthRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        return ResponseEntity.ok(
                ApiResponse.success(reauthService.reauthenticateWithApple(userId, request), "재인증이 완료되었습니다"));
    }

    @PostMapping("/find-id")
    public ResponseEntity<ApiResponse<FindIdResponse>> findId(@Valid @RequestBody FindIdRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountRecoveryService.findLoginId(request), "아이디를 조회했습니다"));
    }

    @PostMapping("/find-password")
    public ResponseEntity<ApiResponse<FindPasswordResponse>> findPassword(@Valid @RequestBody FindPasswordRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(accountRecoveryService.initiatePasswordReset(request), "비밀번호 재설정 토큰이 발급되었습니다"));
    }

    @PatchMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountRecoveryService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "비밀번호가 재설정되었습니다"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildClearCookie().toString())
                .body(ApiResponse.success(null, "로그아웃 되었습니다"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(Authentication authentication) {
        String userId = authentication.getName();
        sessionService.revokeAllSessions(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildClearCookie().toString())
                .body(ApiResponse.success(null, "모든 기기에서 로그아웃 되었습니다"));
    }

    private ResponseCookie buildRefreshCookie(String value) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(REFRESH_COOKIE_MAX_AGE_SECONDS)
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie buildClearCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

}
