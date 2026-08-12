package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.FindIdRequest;
import com.todo.domain.auth.dto.request.FindPasswordRequest;
import com.todo.domain.auth.dto.request.ResetPasswordRequest;
import com.todo.domain.auth.dto.response.FindIdResponse;
import com.todo.domain.auth.dto.response.FindPasswordResponse;
import com.todo.domain.auth.entity.PasswordResetToken;
import com.todo.domain.auth.repository.PasswordResetTokenRepository;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = LocalDateTime.of(2026, 8, 1, 12, 0).atZone(KST).toInstant();
    private static final String EMAIL = "user@example.com";
    private static final String EMAIL_TOKEN = "email-verification-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    private AccountRecoveryService accountRecoveryService;
    private User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, KST);
        accountRecoveryService = new AccountRecoveryService(
                userRepository, passwordResetTokenRepository, emailVerificationService, passwordEncoder, clock,
                notificationService, notificationMessageFactory);
        user = User.create("localUser", "encodedPwd", "닉네임", null);
        user.assignEmail(EMAIL);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private String hashOf(String rawToken) {
        return (String) ReflectionTestUtils.invokeMethod(accountRecoveryService, "hash", rawToken);
    }

    private User appleUser() {
        User apple = User.createAppleUser("apple-social-1", "닉네임", null);
        apple.assignEmail(EMAIL);
        ReflectionTestUtils.setField(apple, "id", 1L);
        return apple;
    }

    @Test
    void 이메일_인증을_거쳐_아이디를_조회한다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        FindIdResponse response = accountRecoveryService.findLoginId(new FindIdRequest(EMAIL, EMAIL_TOKEN));

        assertThat(response.loginId()).isEqualTo("localUser");
        verify(emailVerificationService).validateAndConsume(EMAIL_TOKEN, EMAIL);
    }

    @Test
    void 가입되지_않은_이메일은_아이디_조회에_실패한다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountRecoveryService.findLoginId(new FindIdRequest(EMAIL, EMAIL_TOKEN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void Apple_계정은_아이디_찾기를_쓸_수_없다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(appleUser()));

        assertThatThrownBy(() -> accountRecoveryService.findLoginId(new FindIdRequest(EMAIL, EMAIL_TOKEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apple 계정");
    }

    @Test
    void 이메일_인증에_실패하면_아이디를_조회하지_않는다() {
        org.mockito.BDDMockito.willThrow(new BusinessException("이메일 인증 토큰이 유효하지 않습니다.", HttpStatus.BAD_REQUEST))
                .given(emailVerificationService).validateAndConsume(EMAIL_TOKEN, EMAIL);

        assertThatThrownBy(() -> accountRecoveryService.findLoginId(new FindIdRequest(EMAIL, EMAIL_TOKEN)))
                .isInstanceOf(BusinessException.class);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void 비밀번호_재설정_토큰을_발급하면_기존_토큰을_먼저_지운다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        FindPasswordResponse response = accountRecoveryService.initiatePasswordReset(new FindPasswordRequest(EMAIL, EMAIL_TOKEN));

        assertThat(response.passwordResetToken()).isNotBlank();
        verify(passwordResetTokenRepository).deleteByUserId(1L);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isNotEqualTo(response.passwordResetToken())
                .isEqualTo(hashOf(response.passwordResetToken()));
    }

    @Test
    void 가입되지_않은_이메일은_비밀번호_찾기에_실패한다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountRecoveryService.initiatePasswordReset(new FindPasswordRequest(EMAIL, EMAIL_TOKEN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void Apple_계정은_비밀번호_찾기를_쓸_수_없다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(appleUser()));

        assertThatThrownBy(() -> accountRecoveryService.initiatePasswordReset(new FindPasswordRequest(EMAIL, EMAIL_TOKEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apple 계정");
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void 유효한_토큰으로_비밀번호를_재설정한다() {
        PasswordResetToken token = PasswordResetToken.create(user, hashOf("raw-token"), LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(10));
        given(passwordResetTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));
        given(passwordResetTokenRepository.markAsUsed(any(), any())).willReturn(1);
        given(passwordEncoder.encode("newPassword1!")).willReturn("encodedNewPassword");
        NotificationMessage message = new NotificationMessage(NotificationType.PASSWORD_CHANGED, "title", "content");
        given(notificationMessageFactory.passwordChanged()).willReturn(message);

        accountRecoveryService.resetPassword(new ResetPasswordRequest("raw-token", "newPassword1!", "newPassword1!"));

        assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
        verify(passwordResetTokenRepository).markAsUsed(any(), any());
        // markAsUsed(clearAutomatically=true)가 영속성 컨텍스트를 비워 user를 detach시키므로,
        // 변경 후 명시적으로 저장하지 않으면 실제로는 DB에 반영되지 않는다.
        verify(userRepository).save(user);
        verify(notificationService).send(user, null, message, null);
    }

    @Test
    void 새_비밀번호_확인이_다르면_재설정하지_않는다() {
        assertThatThrownBy(() -> accountRecoveryService.resetPassword(
                new ResetPasswordRequest("raw-token", "newPassword1!", "다름")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
        verify(passwordResetTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void 존재하지_않는_토큰은_거부한다() {
        given(passwordResetTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountRecoveryService.resetPassword(
                new ResetPasswordRequest("없는토큰", "newPassword1!", "newPassword1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않은 재설정 토큰입니다.");
    }

    @Test
    void 이미_사용된_토큰은_거부한다() {
        PasswordResetToken token = PasswordResetToken.create(user, hashOf("raw-token"), LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(10));
        token.markAsUsed(LocalDateTime.now(Clock.fixed(NOW, KST)).minusMinutes(1));
        given(passwordResetTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));

        assertThatThrownBy(() -> accountRecoveryService.resetPassword(
                new ResetPasswordRequest("raw-token", "newPassword1!", "newPassword1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("만료되었거나 이미 사용된 재설정 토큰입니다.");
    }

    @Test
    void 만료된_토큰은_거부한다() {
        PasswordResetToken token = PasswordResetToken.create(user, hashOf("raw-token"), LocalDateTime.now(Clock.fixed(NOW, KST)).minusSeconds(1));
        given(passwordResetTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));

        assertThatThrownBy(() -> accountRecoveryService.resetPassword(
                new ResetPasswordRequest("raw-token", "newPassword1!", "newPassword1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("만료되었거나 이미 사용된 재설정 토큰입니다.");
    }

    @Test
    void 동시에_소비되어_갱신_행이_없으면_거부한다() {
        PasswordResetToken token = PasswordResetToken.create(user, hashOf("raw-token"), LocalDateTime.now(Clock.fixed(NOW, KST)).plusMinutes(10));
        given(passwordResetTokenRepository.findByTokenHash(hashOf("raw-token"))).willReturn(Optional.of(token));
        given(passwordResetTokenRepository.markAsUsed(any(), any())).willReturn(0);

        assertThatThrownBy(() -> accountRecoveryService.resetPassword(
                new ResetPasswordRequest("raw-token", "newPassword1!", "newPassword1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("만료되었거나 이미 사용된 재설정 토큰입니다.");
    }
}
