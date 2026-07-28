package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.ConsentRequest;
import com.todo.domain.auth.dto.request.EmailSendRequest;
import com.todo.domain.auth.dto.request.EmailVerifyRequest;
import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.EmailVerifyResponse;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.service.AuthService;
import com.todo.domain.auth.service.EmailVerificationService;
import com.todo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailCode(@Valid @RequestBody EmailSendRequest request) {
        emailVerificationService.sendCode(request.email());
        return ResponseEntity.accepted()
                .body(ApiResponse.success(null, "인증 코드 발송 요청이 접수되었습니다."));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<EmailVerifyResponse>> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
        String token = emailVerificationService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.success(new EmailVerifyResponse(token)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.success(null, "로그아웃 되었습니다"));
    }

    @PostMapping("/consents")
    public ResponseEntity<ApiResponse<Void>> saveConsent(
            @Valid @RequestBody ConsentRequest request,
            Authentication authentication
    ) {
        authService.saveConsent(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
