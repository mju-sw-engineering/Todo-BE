package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserConsentRepository userConsentRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private FileService fileService;
    @Mock
    private EmailVerificationService emailVerificationService;

    // ──────────── signup ────────────

    @Test
    void 회원가입_성공() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", "profiles/1/a.png", false);
        given(userRepository.existsByLoginId("user1")).willReturn(false);
        given(passwordEncoder.encode("password123!")).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        given(fileService.resolveImageUrl("profiles/1/a.png")).willReturn("https://cdn.example.com/a.png");

        SignupResponse response = authService.signup(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.loginId()).isEqualTo("user1");
        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example.com/a.png");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded");

        ArgumentCaptor<List<UserConsent>> consentCaptor = ArgumentCaptor.forClass(List.class);
        then(userConsentRepository).should().saveAll(consentCaptor.capture());
        List<UserConsent> savedConsents = consentCaptor.getValue();
        assertThat(savedConsents).hasSize(2);
        assertThat(savedConsents).extracting(UserConsent::getConsentType)
                .containsExactlyInAnyOrder(ConsentType.TERMS, ConsentType.PRIVACY);
    }

    @Test
    void 회원가입_성공_마케팅_동의_시_동의_이력_3건_저장() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", null, true);
        given(userRepository.existsByLoginId("user1")).willReturn(false);
        given(passwordEncoder.encode("password123!")).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        authService.signup(request);

        ArgumentCaptor<List<UserConsent>> consentCaptor = ArgumentCaptor.forClass(List.class);
        then(userConsentRepository).should().saveAll(consentCaptor.capture());
        List<UserConsent> savedConsents = consentCaptor.getValue();
        assertThat(savedConsents).hasSize(3);
        assertThat(savedConsents).extracting(UserConsent::getConsentType)
                .containsExactlyInAnyOrder(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.MARKETING);
    }

    @Test
    void 회원가입은_이메일_인증_토큰이_유효하지_않으면_예외를_던진다() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", null, false);
        given(userRepository.existsByLoginId("user1")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new BusinessException("유효하지 않은 이메일 인증 토큰입니다.", HttpStatus.BAD_REQUEST))
                .given(emailVerificationService).validateAndConsume("test-token", "user@example.com");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않은 이메일 인증 토큰입니다.");
        then(userRepository).should(never()).save(any());
    }

    @Test
    void 회원가입은_비밀번호_확인이_다르면_예외를_던진다() {
        SignupRequest request = signupRequest("user1", "password123!", "different", "닉네임", null, false);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비밀번호가 일치하지 않습니다.");
        then(userRepository).should(never()).save(any());
    }

    @Test
    void 회원가입은_중복_아이디면_예외를_던진다() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", null, false);
        given(userRepository.existsByLoginId("user1")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 사용 중인 아이디입니다.");
        then(userRepository).should(never()).save(any());
    }

    // ──────────── login ────────────

    @Test
    void 로그인_성공() {
        User user = User.create("user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded")).willReturn(true);
        given(jwtUtil.generateToken(1L)).willReturn("access-token");
        given(jwtUtil.generateRefreshToken()).willReturn("refresh-uuid");
        given(jwtUtil.refreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(14));

        LoginResult result = authService.login(new LoginRequest("user1", "password123!"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-uuid");
        then(refreshTokenRepository).should().save(any(RefreshToken.class));
    }

    @Test
    void 로그인은_사용자가_없으면_예외를_던진다() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "password123!")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void 로그인은_비밀번호가_다르면_예외를_던진다() {
        User user = User.create("user1", "encoded", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user1", "wrong")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    // ──────────── refresh ────────────

    @Test
    void 리프레시_성공() {
        User user = User.create("user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        RefreshToken token = RefreshToken.create(user, "old-uuid", LocalDateTime.now().plusDays(14));
        given(refreshTokenRepository.findByToken("old-uuid")).willReturn(Optional.of(token));
        given(jwtUtil.generateToken(1L)).willReturn("new-access-token");
        given(jwtUtil.generateRefreshToken()).willReturn("new-uuid");
        given(jwtUtil.refreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(14));

        LoginResult result = authService.refresh("old-uuid");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-uuid");
        assertThat(token.isUsed()).isTrue();
        then(refreshTokenRepository).should().save(any(RefreshToken.class));
    }

    @Test
    void 리프레시_토큰이_없으면_예외를_던진다() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("리프레시 토큰");
    }

    @Test
    void 리프레시_토큰이_DB에_없으면_예외를_던진다() {
        given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은");
    }

    @Test
    void 리프레시_재사용_감지시_해당_사용자_토큰_전체_삭제하고_예외를_던진다() {
        User user = User.create("user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        RefreshToken usedToken = RefreshToken.create(user, "used-uuid", LocalDateTime.now().plusDays(14));
        usedToken.markAsUsed();
        given(refreshTokenRepository.findByToken("used-uuid")).willReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> authService.refresh("used-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은");
        then(refreshTokenRepository).should().deleteByUserId(1L);
    }

    @Test
    void 리프레시_토큰_만료시_예외를_던진다() {
        User user = User.create("user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        RefreshToken expiredToken = RefreshToken.create(user, "expired-uuid", LocalDateTime.now().minusSeconds(1));
        given(refreshTokenRepository.findByToken("expired-uuid")).willReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("expired-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("만료");
    }

    // ──────────── logout ────────────

    @Test
    void 로그아웃_성공() {
        User user = User.create("user1", "encoded", "닉네임", null);
        RefreshToken token = RefreshToken.create(user, "my-uuid", LocalDateTime.now().plusDays(14));
        given(refreshTokenRepository.findByToken("my-uuid")).willReturn(Optional.of(token));

        authService.logout("my-uuid");

        then(refreshTokenRepository).should().delete(token);
    }

    @Test
    void 로그아웃_토큰이_null이면_아무것도_하지_않는다() {
        authService.logout(null);

        then(refreshTokenRepository).should(never()).findByToken(any());
    }

    // ──────────── loadUserByUsername ────────────

    @Test
    void 사용자_상세정보를_로드한다() {
        User user = User.create("user1", "encoded", "닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserDetails userDetails = authService.loadUserByUsername("1");

        assertThat(userDetails.getUsername()).isEqualTo("1");
        assertThat(userDetails.getPassword()).isEqualTo("encoded");
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void 사용자_상세정보는_사용자가_없으면_예외를_던진다() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loadUserByUsername("999"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    private SignupRequest signupRequest(
            String loginId,
            String password,
            String passwordConfirm,
            String nickname,
            String profileImageKey,
            boolean marketingAgreed
    ) {
        return new SignupRequest(
                "user@example.com",
                "test-token",
                loginId,
                password,
                passwordConfirm,
                nickname,
                profileImageKey,
                true,
                true,
                marketingAgreed
        );
    }
}
