package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.EmailSendRequest;
import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.service.AuthService;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.auth.service.EmailVerificationService;
import com.todo.global.exception.BusinessException;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, emailVerificationService, reauthService);
        ReflectionTestUtils.setField(controller, "cookieSecure", false);
    }

    @Test
    void 이메일_인증코드_발송_요청은_202를_반환한다() {
        EmailSendRequest request = new EmailSendRequest("user@example.com");

        ResponseEntity<ApiResponse<Void>> response = controller.sendEmailCode(request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().getMessage()).isEqualTo("인증 코드 발송 요청이 접수되었습니다.");
        then(emailVerificationService).should().sendCode("user@example.com");
    }

    @Test
    void 회원가입_응답을_반환한다() {
        SignupRequest request = new SignupRequest(
                "user@example.com", "token", "user1", "password123!", "password123!", "닉네임", null, true, true, false);
        SignupResponse signupResponse = new SignupResponse(1L, "user1", "닉네임", null);
        given(authService.signup(request)).willReturn(signupResponse);

        ResponseEntity<ApiResponse<SignupResponse>> response = controller.signup(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(signupResponse);
    }

    @Test
    void 로그인_응답을_반환하고_쿠키를_설정한다() {
        LoginRequest request = new LoginRequest("user1", "password");
        given(authService.login(request)).willReturn(new LoginResult("access-token", "refresh-uuid"));

        ResponseEntity<ApiResponse<LoginResponse>> response = controller.login(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().accessToken()).isEqualTo("access-token");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotEmpty();
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("refreshToken=refresh-uuid");
    }

    @Test
    void 토큰_재발급_성공() {
        given(authService.refresh("old-uuid")).willReturn(new LoginResult("new-access", "new-uuid"));

        ResponseEntity<ApiResponse<LoginResponse>> response = controller.refresh("old-uuid");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().accessToken()).isEqualTo("new-access");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("refreshToken=new-uuid");
    }

    @Test
    void 토큰_재발급_실패시_예외를_전파한다() {
        given(authService.refresh(null)).willThrow(new BusinessException("리프레시 토큰이 없습니다.", HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> controller.refresh(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("리프레시 토큰");
    }

    @Test
    void 로그아웃_응답을_반환하고_쿠키를_삭제한다() {
        ResponseEntity<ApiResponse<Void>> response = controller.logout("my-uuid");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("로그아웃 되었습니다");
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("refreshToken=");
        assertThat(cookie).contains("Max-Age=0");
        then(authService).should().logout("my-uuid");
    }
}
