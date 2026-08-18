package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.service.AppleAuthService;
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.ClientIpResolver;
import com.todo.global.ratelimit.SimpleRateLimiter;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppleAuthControllerTest {

    @Mock
    private AppleAuthService appleAuthService;
    @Mock
    private SimpleRateLimiter rateLimiter;
    @Mock
    private ClientIpResolver clientIpResolver;

    private AppleAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AppleAuthController(appleAuthService, rateLimiter, clientIpResolver);
        ReflectionTestUtils.setField(controller, "cookieSecure", false);
        lenient().when(clientIpResolver.resolve(any())).thenReturn("1.2.3.4");
        lenient().when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    @Test
    void Apple_로그인_기존_회원이면_200과_액세스_토큰을_반환한다() {
        AppleLoginRequest request = new AppleLoginRequest("id-token", "auth-code", "nonce", null);
        given(appleAuthService.appleLogin(request))
                .willReturn(new AppleAuthService.AppleLoginResult.LoggedIn(
                        new LoginResult("access-token", "refresh-uuid")));

        ResponseEntity<ApiResponse<?>> response = controller.appleLogin(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).isEqualTo("로그인되었습니다");
        assertThat(((LoginResponse) response.getBody().getData()).accessToken()).isEqualTo("access-token");
    }

    @Test
    void Apple_로그인_신규_회원이면_202와_setup_token을_반환한다() {
        AppleLoginRequest request = new AppleLoginRequest("id-token", "auth-code", "nonce", null);
        given(appleAuthService.appleLogin(request))
                .willReturn(new AppleAuthService.AppleLoginResult.SetupRequired("setup-token"));

        ResponseEntity<ApiResponse<?>> response = controller.appleLogin(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getMessage()).isEqualTo("추가 정보 입력이 필요합니다");
    }

    @Test
    void Apple_로그인은_IP_기준_한도를_넘으면_검증_전에_거부한다() {
        AppleLoginRequest request = new AppleLoginRequest("id-token", "auth-code", "nonce", null);
        given(rateLimiter.tryAcquire(anyString(), anyInt(), any())).willReturn(false);

        assertThatThrownBy(() -> controller.appleLogin(request, new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        then(appleAuthService).should(never()).appleLogin(any());
    }

    @Test
    void Apple_회원가입_완료_후_액세스_토큰을_반환한다() {
        AppleCompleteRequest request =
                new AppleCompleteRequest("setup-token", "닉네임", null, true, true, false, null);
        given(appleAuthService.appleComplete(request))
                .willReturn(new LoginResult("access-token", "refresh-uuid"));

        ResponseEntity<ApiResponse<LoginResponse>> response = controller.appleComplete(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).isEqualTo("회원가입이 완료되었습니다");
        assertThat(response.getBody().getData().accessToken()).isEqualTo("access-token");
    }
}
