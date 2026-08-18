package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.AppleSetupResponse;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.service.AppleAuthService;
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.ClientIpResolver;
import com.todo.global.ratelimit.SimpleRateLimiter;
import com.todo.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth/apple")
@RequiredArgsConstructor
public class AppleAuthController implements AppleAuthControllerDocs {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final long REFRESH_COOKIE_MAX_AGE_SECONDS = 14L * 24 * 60 * 60;
    // identity token 검증(서명·JWKS)이 뒤따르는 공개 엔드포인트라 출처 기준으로 시도를 제한한다.
    private static final int LOGIN_IP_ATTEMPT_LIMIT = 10;
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(1);

    private final AppleAuthService appleAuthService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> appleLogin(
            @Valid @RequestBody AppleLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipKey = "apple-login:ip:" + clientIpResolver.resolve(httpRequest);
        if (!rateLimiter.tryAcquire(ipKey, LOGIN_IP_ATTEMPT_LIMIT, LOGIN_ATTEMPT_WINDOW)) {
            throw new BusinessException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);
        }

        AppleAuthService.AppleLoginResult result = appleAuthService.appleLogin(request);

        if (result instanceof AppleAuthService.AppleLoginResult.LoggedIn logged) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(logged.loginResult().refreshToken()).toString())
                    .body(ApiResponse.success(new LoginResponse(logged.loginResult().accessToken()), "로그인되었습니다"));
        }
        AppleAuthService.AppleLoginResult.SetupRequired setup = (AppleAuthService.AppleLoginResult.SetupRequired) result;
        return ResponseEntity.accepted()
                .body(ApiResponse.success(new AppleSetupResponse(setup.setupToken()), "추가 정보 입력이 필요합니다"));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<LoginResponse>> appleComplete(@Valid @RequestBody AppleCompleteRequest request) {
        LoginResult result = appleAuthService.appleComplete(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.success(new LoginResponse(result.accessToken()), "회원가입이 완료되었습니다"));
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
}
