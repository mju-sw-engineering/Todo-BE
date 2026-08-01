package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.EmailSendRequest;
import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.service.AuthService;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.auth.service.EmailVerificationService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private ReauthService reauthService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Test
    void 이메일_인증코드_발송_요청은_202를_반환한다() {
        AuthController controller = new AuthController(authService, emailVerificationService, reauthService);
        EmailSendRequest request = new EmailSendRequest("user@example.com");

        ResponseEntity<ApiResponse<Void>> response = controller.sendEmailCode(request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().getMessage()).isEqualTo("인증 코드 발송 요청이 접수되었습니다.");
        then(emailVerificationService).should().sendCode("user@example.com");
    }

    @Test
    void 회원가입_응답을_반환한다() {
        AuthController controller = new AuthController(authService, emailVerificationService, reauthService);
        SignupRequest request = new SignupRequest(
                "user@example.com", "token", "user1", "password123!", "password123!", "닉네임", null, true, true, false);
        SignupResponse signupResponse = new SignupResponse(1L, "user1", "닉네임", null);
        given(authService.signup(request)).willReturn(signupResponse);

        ResponseEntity<ApiResponse<SignupResponse>> response = controller.signup(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(signupResponse);
    }

    @Test
    void 로그인_응답을_반환한다() {
        AuthController controller = new AuthController(authService, emailVerificationService, reauthService);
        LoginRequest request = new LoginRequest("user1", "password");
        LoginResponse loginResponse = new LoginResponse("token");
        given(authService.login(request)).willReturn(loginResponse);

        ResponseEntity<ApiResponse<LoginResponse>> response = controller.login(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(loginResponse);
    }

    @Test
    void 로그아웃_응답을_반환한다() {
        AuthController controller = new AuthController(authService, emailVerificationService, reauthService);

        ResponseEntity<ApiResponse<Void>> response = controller.logout();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("로그아웃 되었습니다");
    }
}
