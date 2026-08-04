package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.AppleCompleteRequest;
import com.todo.domain.auth.dto.request.AppleLoginRequest;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AppleAuthServiceTest {

    @InjectMocks
    private AppleAuthService appleAuthService;

    @Mock private AppleIdentityTokenService identityTokenService;
    @Mock private AppleTokenClient appleTokenClient;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;

    private static final String SOCIAL_ID = "apple-user-001";
    private static final String AUTH_CODE = "auth-code-xyz";
    private static final String NONCE = "random-nonce";

    @Test
    void 기존_Apple_유저_로그인_성공() {
        User user = User.createAppleUser(SOCIAL_ID, "기존닉네임");
        ReflectionTestUtils.setField(user, "id", 1L);

        given(identityTokenService.verify(any(), any())).willReturn(SOCIAL_ID);
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.of(user));
        given(appleTokenClient.exchangeForAppleRefreshToken(AUTH_CODE)).willReturn("apple-rt");
        given(jwtUtil.generateToken(1L)).willReturn("access-token");
        given(jwtUtil.generateRefreshToken()).willReturn("refresh-uuid");
        given(jwtUtil.refreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(14));

        AppleAuthService.AppleLoginResult result = appleAuthService.appleLogin(loginRequest());

        assertThat(result).isInstanceOf(AppleAuthService.AppleLoginResult.LoggedIn.class);
        AppleAuthService.AppleLoginResult.LoggedIn logged = (AppleAuthService.AppleLoginResult.LoggedIn) result;
        assertThat(logged.loginResult().accessToken()).isEqualTo("access-token");
        then(refreshTokenRepository).should().save(any(RefreshToken.class));
    }

    @Test
    void 신규_Apple_유저_로그인시_setup_token을_반환한다() {
        given(identityTokenService.verify(any(), any())).willReturn(SOCIAL_ID);
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.empty());
        given(jwtUtil.generateSetupToken(SOCIAL_ID, AUTH_CODE)).willReturn("setup-token-abc");

        AppleAuthService.AppleLoginResult result = appleAuthService.appleLogin(loginRequest());

        assertThat(result).isInstanceOf(AppleAuthService.AppleLoginResult.SetupRequired.class);
        AppleAuthService.AppleLoginResult.SetupRequired setup = (AppleAuthService.AppleLoginResult.SetupRequired) result;
        assertThat(setup.setupToken()).isEqualTo("setup-token-abc");
    }

    @Test
    void Apple_가입_완료_성공() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(SOCIAL_ID);
        given(claims.get("authCode", String.class)).willReturn(AUTH_CODE);

        given(jwtUtil.parseSetupToken("setup-token")).willReturn(claims);
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.empty());

        User saved = User.createAppleUser(SOCIAL_ID, "새닉네임");
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(userRepository.save(any(User.class))).willReturn(saved);
        given(appleTokenClient.exchangeForAppleRefreshToken(AUTH_CODE)).willReturn("apple-rt");
        given(jwtUtil.generateToken(2L)).willReturn("access-token");
        given(jwtUtil.generateRefreshToken()).willReturn("refresh-uuid");
        given(jwtUtil.refreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(14));

        LoginResult result = appleAuthService.appleComplete(new AppleCompleteRequest("setup-token", "새닉네임"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        then(refreshTokenRepository).should().save(any(RefreshToken.class));
    }

    @Test
    void 유효하지_않은_setup_token으로_complete시_예외를_던진다() {
        given(jwtUtil.parseSetupToken("invalid-token")).willThrow(new RuntimeException("expired"));

        assertThatThrownBy(() -> appleAuthService.appleComplete(new AppleCompleteRequest("invalid-token", "닉네임")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("setup token");
    }

    @Test
    void 이미_가입된_소셜_ID로_complete시_예외를_던진다() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(SOCIAL_ID);
        given(claims.get("authCode", String.class)).willReturn(AUTH_CODE);

        given(jwtUtil.parseSetupToken("setup-token")).willReturn(claims);
        given(userRepository.findBySocialId(SOCIAL_ID)).willReturn(Optional.of(User.createAppleUser(SOCIAL_ID, "기존")));

        assertThatThrownBy(() -> appleAuthService.appleComplete(new AppleCompleteRequest("setup-token", "닉네임")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 가입된");
    }

    private AppleLoginRequest loginRequest() {
        return new AppleLoginRequest("identity-token", AUTH_CODE, NONCE);
    }
}
