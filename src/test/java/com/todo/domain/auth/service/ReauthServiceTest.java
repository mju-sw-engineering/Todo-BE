package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.ReauthRequest;
import com.todo.domain.auth.dto.response.ReauthResponse;
import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.entity.ReauthToken;
import com.todo.domain.auth.repository.ReauthTokenRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.SimpleRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReauthServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = LocalDateTime.of(2026, 8, 1, 12, 0).atZone(KST).toInstant();

    @Mock
    private UserRepository userRepository;
    @Mock
    private ReauthTokenRepository reauthTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SimpleRateLimiter rateLimiter;

    private ReauthService reauthService;
    private User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, KST);
        reauthService = new ReauthService(userRepository, reauthTokenRepository, passwordEncoder, rateLimiter, clock);
        user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private void givenPasswordMatches() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).willReturn(true);
        given(passwordEncoder.matches("rawPwd", "encodedPwd")).willReturn(true);
    }

    private ReauthToken storedToken(String rawToken, ReauthPurpose purpose, LocalDateTime expiresAt) {
        // 서비스가 저장한 해시를 그대로 재사용하기 위해 발급 경로를 한 번 태운다.
        return ReauthToken.create(user, hashOf(rawToken), purpose, expiresAt);
    }

    private String hashOf(String rawToken) {
        return (String) ReflectionTestUtils.invokeMethod(reauthService, "hash", rawToken);
    }

    @Test
    void 비밀번호가_맞으면_토큰과_만료시각을_발급한다() {
        givenPasswordMatches();

        ReauthResponse response = reauthService.reauthenticate("1", new ReauthRequest("rawPwd", ReauthPurpose.WITHDRAWAL));

        assertThat(response.reauthToken()).isNotBlank();
        assertThat(response.expiresAt()).isEqualTo(
                LocalDateTime.now(Clock.fixed(NOW, KST)).plus(ReauthService.TOKEN_TTL).atOffset(java.time.ZoneOffset.ofHours(9)));
    }

    @Test
    void 발급한_토큰은_원문이_아니라_해시로_저장된다() {
        givenPasswordMatches();

        ReauthResponse response = reauthService.reauthenticate("1", new ReauthRequest("rawPwd", ReauthPurpose.WITHDRAWAL));

        ArgumentCaptor<ReauthToken> captor = ArgumentCaptor.forClass(ReauthToken.class);
        verify(reauthTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isNotEqualTo(response.reauthToken())
                .isEqualTo(hashOf(response.reauthToken()));
    }

    @Test
    void 재발급하면_같은_용도의_이전_토큰은_무효가_된다() {
        givenPasswordMatches();

        reauthService.reauthenticate("1", new ReauthRequest("rawPwd", ReauthPurpose.WITHDRAWAL));

        verify(reauthTokenRepository).deleteByUserIdAndPurpose(1L, ReauthPurpose.WITHDRAWAL);
    }

    @Test
    void 비밀번호가_틀리면_토큰을_발급하지_않는다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).willReturn(true);
        given(passwordEncoder.matches("wrongPwd", "encodedPwd")).willReturn(false);

        assertThatThrownBy(() -> reauthService.reauthenticate("1", new ReauthRequest("wrongPwd", ReauthPurpose.WITHDRAWAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("비밀번호가 일치하지 않습니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(reauthTokenRepository, never()).save(any());
    }

    @Test
    void 시도_한도를_넘으면_비밀번호를_대조하지_않는다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).willReturn(false);

        assertThatThrownBy(() -> reauthService.reauthenticate("1", new ReauthRequest("rawPwd", ReauthPurpose.WITHDRAWAL)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        verifyNoInteractions(passwordEncoder, reauthTokenRepository);
    }

    @Test
    void 유효한_토큰을_소비하면_사용자_ID를_돌려주고_사용됨으로_표시한다() {
        ReauthToken token = storedToken("raw-token", ReauthPurpose.WITHDRAWAL, LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(3));
        given(reauthTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));

        given(reauthTokenRepository.markAsUsed(any(), any())).willReturn(1);

        Long userId = reauthService.consume("1", "raw-token", ReauthPurpose.WITHDRAWAL);

        assertThat(userId).isEqualTo(1L);
        verify(reauthTokenRepository).markAsUsed(any(), any());
    }

    @Test
    void 이미_사용된_토큰은_다시_통과하지_못한다() {
        ReauthToken token = storedToken("raw-token", ReauthPurpose.WITHDRAWAL, LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(3));
        token.markAsUsed(LocalDateTime.now(Clock.fixed(NOW, KST)).minusMinutes(1));
        given(reauthTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));

        assertThatThrownBy(() -> reauthService.consume("1", "raw-token", ReauthPurpose.WITHDRAWAL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용된 재인증입니다");
    }

    @Test
    void 만료된_토큰은_통과하지_못한다() {
        ReauthToken token = storedToken("raw-token", ReauthPurpose.WITHDRAWAL, LocalDateTime.now(Clock.fixed(NOW, KST)).minusSeconds(1));
        given(reauthTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));

        assertThatThrownBy(() -> reauthService.consume("1", "raw-token", ReauthPurpose.WITHDRAWAL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("재인증이 만료되었습니다");
    }

    @Test
    void 다른_사용자의_토큰은_통과하지_못한다() {
        ReauthToken token = storedToken("raw-token", ReauthPurpose.WITHDRAWAL, LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(3));
        given(reauthTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));

        assertThatThrownBy(() -> reauthService.consume("other", "raw-token", ReauthPurpose.WITHDRAWAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(reauthTokenRepository, never()).markAsUsed(any(), any());
    }

    @Test
    void 존재하지_않는_토큰은_통과하지_못한다() {
        given(reauthTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> reauthService.consume("1", "없는토큰", ReauthPurpose.WITHDRAWAL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("재인증이 필요합니다");
    }

    @Test
    void 토큰이_비어_있으면_조회하지_않고_거부한다() {
        assertThatThrownBy(() -> reauthService.consume("1", "  ", ReauthPurpose.WITHDRAWAL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("재인증이 필요합니다");

        verifyNoInteractions(reauthTokenRepository);
    }

    @Test
    void 다른_용도로_발급된_토큰은_통과하지_못한다() {
        ReauthToken token = storedToken("raw-token", ReauthPurpose.WITHDRAWAL, LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(3));
        ReflectionTestUtils.setField(token, "purpose", null);
        given(reauthTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));

        assertThatThrownBy(() -> reauthService.consume("1", "raw-token", ReauthPurpose.WITHDRAWAL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이 작업에 사용할 수 없는 재인증입니다");
    }

    @Test
    void 동시에_소비되어_갱신_행이_없으면_거부한다() {
        ReauthToken token = storedToken("raw-token", ReauthPurpose.WITHDRAWAL, LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(3));
        given(reauthTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));
        // 검증을 통과한 직후 다른 요청이 먼저 소비한 상황
        given(reauthTokenRepository.markAsUsed(any(), any())).willReturn(0);

        assertThatThrownBy(() -> reauthService.consume("1", "raw-token", ReauthPurpose.WITHDRAWAL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용된 재인증입니다");
    }
}
