package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.EmailSendRequest;
import com.todo.domain.auth.dto.request.EmailVerifyRequest;
import com.todo.domain.auth.dto.request.FindIdRequest;
import com.todo.domain.auth.dto.request.FindPasswordRequest;
import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.ReauthRequest;
import com.todo.domain.auth.dto.request.ResetPasswordRequest;
import com.todo.domain.auth.entity.ReauthPurpose;
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
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.ClientIpResolver;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private ReauthService reauthService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private AccountRecoveryService accountRecoveryService;
    @Mock
    private SessionService sessionService;
    @Mock
    private ClientIpResolver clientIpResolver;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                authService, emailVerificationService, reauthService, accountRecoveryService, sessionService,
                clientIpResolver);
        ReflectionTestUtils.setField(controller, "cookieSecure", false);
        lenient().when(clientIpResolver.resolve(any())).thenReturn("1.2.3.4");
    }

    @Test
    void 아이디_찾기_응답을_반환한다() {
        FindIdRequest request = new FindIdRequest("user@example.com", "email-token");
        given(accountRecoveryService.findLoginId(request)).willReturn(new FindIdResponse("user1"));

        ResponseEntity<ApiResponse<FindIdResponse>> response = controller.findId(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().loginId()).isEqualTo("user1");
    }

    @Test
    void 비밀번호_찾기_응답을_반환한다() {
        FindPasswordRequest request = new FindPasswordRequest("user@example.com", "email-token");
        given(accountRecoveryService.initiatePasswordReset(request)).willReturn(new FindPasswordResponse("reset-token"));

        ResponseEntity<ApiResponse<FindPasswordResponse>> response = controller.findPassword(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().passwordResetToken()).isEqualTo("reset-token");
    }

    @Test
    void 비밀번호_재설정_응답을_반환한다() {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "newPwd1!", "newPwd1!");

        ResponseEntity<ApiResponse<Void>> response = controller.resetPassword(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("비밀번호가 재설정되었습니다");
        then(accountRecoveryService).should().resetPassword(request);
    }

    @Test
    void 이메일_인증_코드_검증_성공시_토큰을_반환한다() {
        EmailVerifyRequest request = new EmailVerifyRequest("user@example.com", "123456");
        given(emailVerificationService.verifyCode("user@example.com", "123456")).willReturn("verify-token");

        ResponseEntity<ApiResponse<EmailVerifyResponse>> response = controller.verifyEmailCode(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("이메일 인증이 완료되었습니다");
        assertThat(response.getBody().getData().emailVerificationToken()).isEqualTo("verify-token");
    }

    @Test
    void 재인증_성공시_재인증_토큰을_반환한다() {
        ReauthRequest request = new ReauthRequest("password123!", ReauthPurpose.WITHDRAWAL);
        ReauthResponse reauthResponse = new ReauthResponse("reauth-token",
                OffsetDateTime.of(2026, 8, 8, 12, 5, 0, 0, ZoneOffset.ofHours(9)));
        given(reauthService.reauthenticate("user1", request)).willReturn(reauthResponse);
        org.springframework.security.authentication.TestingAuthenticationToken auth =
                new org.springframework.security.authentication.TestingAuthenticationToken("user1", null);

        ResponseEntity<ApiResponse<ReauthResponse>> response = controller.reauthenticate(request, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("재인증이 완료되었습니다");
        assertThat(response.getBody().getData().reauthToken()).isEqualTo("reauth-token");
    }

    @Test
    void 이메일_인증코드_발송_요청은_202를_반환한다() {
        EmailSendRequest request = new EmailSendRequest("user@example.com");

        ResponseEntity<ApiResponse<Void>> response = controller.sendEmailCode(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().getMessage()).isEqualTo("인증 코드 발송 요청이 접수되었습니다.");
        then(emailVerificationService).should().sendCode("user@example.com", "1.2.3.4");
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
        LoginRequest request = new LoginRequest("user1", "password", null);
        given(authService.login(request, "1.2.3.4")).willReturn(new LoginResult("access-token", "refresh-uuid"));

        ResponseEntity<ApiResponse<LoginResponse>> response = controller.login(request, new MockHttpServletRequest());

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

    @Test
    void 로그인_쿠키는_보안_속성을_모두_포함한다() {
        LoginRequest request = new LoginRequest("user1", "password", null);
        given(authService.login(request, "1.2.3.4")).willReturn(new LoginResult("access-token", "refresh-uuid"));

        String cookie = controller.login(request, new MockHttpServletRequest()).getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie)
                .contains("refreshToken=refresh-uuid")
                .contains("HttpOnly")
                .contains("Path=/api/auth")
                .contains("SameSite=Strict")
                .contains("Max-Age=1209600");
    }

    @Test
    void 재발급_쿠키는_보안_속성을_모두_포함한다() {
        given(authService.refresh("old-uuid")).willReturn(new LoginResult("new-access", "new-uuid"));

        String cookie = controller.refresh("old-uuid").getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie)
                .contains("refreshToken=new-uuid")
                .contains("HttpOnly")
                .contains("Path=/api/auth")
                .contains("SameSite=Strict")
                .contains("Max-Age=1209600");
    }

    @Test
    void 전체_로그아웃_응답을_반환하고_쿠키를_삭제한다() {
        org.springframework.security.authentication.TestingAuthenticationToken auth =
                new org.springframework.security.authentication.TestingAuthenticationToken("user1", null);

        ResponseEntity<ApiResponse<Void>> response = controller.logoutAll(auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("모든 기기에서 로그아웃 되었습니다");
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("refreshToken=");
        assertThat(cookie).contains("Max-Age=0");
        then(sessionService).should().revokeAllSessions("user1");
    }

    @Test
    void 로그아웃_쿠키는_같은_속성으로_즉시_만료된다() {
        String cookie = controller.logout("my-uuid").getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie)
                .contains("refreshToken=;")
                .contains("HttpOnly")
                .contains("Path=/api/auth")
                .contains("SameSite=Strict")
                .contains("Max-Age=0");
    }

    @Test
    void cookieSecure가_true면_세_경로의_쿠키에_모두_Secure가_붙는다() {
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        LoginRequest request = new LoginRequest("user1", "password", null);
        given(authService.login(request, "1.2.3.4")).willReturn(new LoginResult("access-token", "refresh-uuid"));
        given(authService.refresh("old-uuid")).willReturn(new LoginResult("new-access", "new-uuid"));

        assertThat(controller.login(request, new MockHttpServletRequest()).getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("; Secure");
        assertThat(controller.refresh("old-uuid").getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("; Secure");
        assertThat(controller.logout("my-uuid").getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("; Secure");
    }

    @Test
    void cookieSecure가_false면_세_경로의_쿠키에_Secure가_붙지_않는다() {
        LoginRequest request = new LoginRequest("user1", "password", null);
        given(authService.login(request, "1.2.3.4")).willReturn(new LoginResult("access-token", "refresh-uuid"));
        given(authService.refresh("old-uuid")).willReturn(new LoginResult("new-access", "new-uuid"));

        assertThat(controller.login(request, new MockHttpServletRequest()).getHeaders().getFirst(HttpHeaders.SET_COOKIE)).doesNotContain("; Secure");
        assertThat(controller.refresh("old-uuid").getHeaders().getFirst(HttpHeaders.SET_COOKIE)).doesNotContain("; Secure");
        assertThat(controller.logout("my-uuid").getHeaders().getFirst(HttpHeaders.SET_COOKIE)).doesNotContain("; Secure");
    }
}
