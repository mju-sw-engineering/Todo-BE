package com.todo.domain.auth.controller;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.service.AuthService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Test
    void 회원가입_응답을_반환한다() {
        AuthController controller = new AuthController(authService);
        SignupRequest request = new SignupRequest();
        SignupResponse signupResponse = new SignupResponse(1L, "user1", "닉네임", null);
        given(authService.signup(request)).willReturn(signupResponse);

        ResponseEntity<ApiResponse<SignupResponse>> response = controller.signup(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(signupResponse);
    }

    @Test
    void 로그인_응답을_반환한다() {
        AuthController controller = new AuthController(authService);
        LoginRequest request = new LoginRequest("user1", "password");
        LoginResponse loginResponse = new LoginResponse("token");
        given(authService.login(request)).willReturn(loginResponse);

        ResponseEntity<ApiResponse<LoginResponse>> response = controller.login(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(loginResponse);
    }

    @Test
    void 로그아웃_응답을_반환한다() {
        AuthController controller = new AuthController(authService);

        ResponseEntity<ApiResponse<Void>> response = controller.logout();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("로그아웃 되었습니다");
    }
}
