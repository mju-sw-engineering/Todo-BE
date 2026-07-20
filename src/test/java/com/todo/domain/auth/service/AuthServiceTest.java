package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.service.FileService;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private FileService fileService;
    @Mock
    private EmailVerificationService emailVerificationService;

    @Test
    void 회원가입_성공() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", "profiles/1/a.png", true);
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

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
        assertThat(captor.getValue().isTermsAgreed()).isTrue();
        assertThat(captor.getValue().getTermsAgreedAt()).isNotNull();
    }

    @Test
    void 회원가입은_이메일_인증_토큰이_유효하지_않으면_예외를_던진다() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", null, true);
        given(userRepository.existsByLoginId("user1")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new BusinessException("유효하지 않은 이메일 인증 토큰입니다.", HttpStatus.BAD_REQUEST))
                .given(emailVerificationService).validateAndConsume("test-token", "user@example.com");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않은 이메일 인증 토큰입니다.");
        then(userRepository).should(never()).save(any());
    }

    @Test
    void 회원가입은_개인정보_동의_안하면_예외를_던진다() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", null, false);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("개인정보 처리방침에 동의해야 합니다.");
        then(userRepository).should(never()).save(any());
    }

    @Test
    void 회원가입은_비밀번호_확인이_다르면_예외를_던진다() {
        SignupRequest request = signupRequest("user1", "password123!", "different", "닉네임", null, true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
        then(userRepository).should(never()).save(any());
    }

    @Test
    void 회원가입은_중복_아이디면_예외를_던진다() {
        SignupRequest request = signupRequest("user1", "password123!", "password123!", "닉네임", null, true);
        given(userRepository.existsByLoginId("user1")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 아이디입니다.");
        then(userRepository).should(never()).save(any());
    }

    @Test
    void 로그인_성공() {
        User user = User.create("user1", "encoded", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded")).willReturn(true);
        given(jwtUtil.generateToken("user1")).willReturn("access-token");

        LoginResponse response = authService.login(new LoginRequest("user1", "password123!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void 로그인은_사용자가_없으면_예외를_던진다() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "password123!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void 로그인은_비밀번호가_다르면_예외를_던진다() {
        User user = User.create("user1", "encoded", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user1", "wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void 사용자_상세정보를_로드한다() {
        User user = User.create("user1", "encoded", "닉네임", null);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));

        UserDetails userDetails = authService.loadUserByUsername("user1");

        assertThat(userDetails.getUsername()).isEqualTo("user1");
        assertThat(userDetails.getPassword()).isEqualTo("encoded");
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void 사용자_상세정보는_사용자가_없으면_예외를_던진다() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    private SignupRequest signupRequest(
            String loginId,
            String password,
            String passwordConfirm,
            String nickname,
            String profileImageKey,
            boolean termsAgreed
    ) {
        SignupRequest request = new SignupRequest();
        request.setEmail("user@example.com");
        request.setEmailVerificationToken("test-token");
        request.setLoginId(loginId);
        request.setPassword(password);
        request.setPasswordConfirm(passwordConfirm);
        request.setNickname(nickname);
        request.setProfileImageKey(profileImageKey);
        request.setTermsAgreed(termsAgreed);
        return request;
    }
}
