package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.EmailVerification;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void 인증코드_발송_성공() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        ReflectionTestUtils.setField(emailVerificationService, "fromEmail", "test@example.com");

        emailVerificationService.sendCode("user@example.com");

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        then(emailVerificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getCode()).hasSize(6);
        assertThat(captor.getValue().isExpired()).isFalse();
        then(mailSender).should().send(mimeMessage);
    }

    @Test
    void 인증코드_확인_성공() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        given(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(ev));

        String token = emailVerificationService.verifyCode("user@example.com", "123456");

        assertThat(token).isNotBlank();
        assertThat(ev.getToken()).isEqualTo(token);
    }

    @Test
    void 인증코드_확인_실패_요청없음() {
        given(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verifyCode("user@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 인증 요청이 없습니다.");
    }

    @Test
    void 인증코드_확인_실패_만료() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().minusSeconds(1));
        given(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(ev));

        assertThatThrownBy(() -> emailVerificationService.verifyCode("user@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 코드가 만료되었습니다.");
    }

    @Test
    void 인증코드_확인_실패_코드불일치() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        given(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(ev));

        assertThatThrownBy(() -> emailVerificationService.verifyCode("user@example.com", "999999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 코드가 올바르지 않습니다.");
    }

    @Test
    void 토큰_검증_및_소비_성공() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ev.verify("test-token");
        given(emailVerificationRepository.findByTokenAndUsedFalse("test-token")).willReturn(Optional.of(ev));

        emailVerificationService.validateAndConsume("test-token", "user@example.com");

        assertThat(ev.isUsed()).isTrue();
    }

    @Test
    void 토큰_검증_실패_토큰없음() {
        given(emailVerificationRepository.findByTokenAndUsedFalse("invalid")).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume("invalid", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("유효하지 않은 이메일 인증 토큰입니다.");
    }

    @Test
    void 토큰_검증_실패_이메일불일치() {
        EmailVerification ev = EmailVerification.create("other@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ev.verify("test-token");
        given(emailVerificationRepository.findByTokenAndUsedFalse("test-token")).willReturn(Optional.of(ev));

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume("test-token", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 인증 정보가 일치하지 않습니다.");
    }
}
