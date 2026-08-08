package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.AppleSetupResponse;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.service.AppleAuthService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/apple")
@RequiredArgsConstructor
public class AppleAuthController implements AppleAuthControllerDocs {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final long REFRESH_COOKIE_MAX_AGE_SECONDS = 14L * 24 * 60 * 60;

    private final AppleAuthService appleAuthService;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> appleLogin(@Valid @RequestBody AppleLoginRequest request) {
        AppleAuthService.AppleLoginResult result = appleAuthService.appleLogin(request);

        if (result instanceof AppleAuthService.AppleLoginResult.LoggedIn logged) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(logged.loginResult().refreshToken()).toString())
                    .body(ApiResponse.success(new LoginResponse(logged.loginResult().accessToken())));
        }
        AppleAuthService.AppleLoginResult.SetupRequired setup = (AppleAuthService.AppleLoginResult.SetupRequired) result;
        return ResponseEntity.accepted()
                .body(ApiResponse.success(new AppleSetupResponse(setup.setupToken())));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<LoginResponse>> appleComplete(@Valid @RequestBody AppleCompleteRequest request) {
        LoginResult result = appleAuthService.appleComplete(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.success(new LoginResponse(result.accessToken())));
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
