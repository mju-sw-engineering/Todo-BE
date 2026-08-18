package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.EmailVerification;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.mail.entity.MailType;
import com.todo.global.mail.service.MailOutboxService;
import com.todo.global.ratelimit.SimpleRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private MailOutboxService mailOutboxService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SimpleRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    @Test
    void 인증코드_발송_성공() {
        emailVerificationService.sendCode("user@example.com", "1.2.3.4");

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        then(emailVerificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getCode()).hasSize(6);
        assertThat(captor.getValue().isExpired()).isFalse();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        then(mailOutboxService).should().enqueue(
                eq(MailType.VERIFICATION),
                eq("user@example.com"),
                eq("[Todo] 이메일 인증 코드"),
                codeCaptor.capture(),
                anyString(),
                any(LocalDateTime.class),
                eq(2)
        );
        assertThat(codeCaptor.getValue()).contains(captor.getValue().getCode());
    }

    @Test
    void 인증코드는_1분_이내_재요청하면_429_예외를_던진다() {
        EmailVerification recent = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ReflectionTestUtils.setField(recent, "createdAt", LocalDateTime.now().minusSeconds(30));
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(recent));

        assertThatThrownBy(() -> emailVerificationService.sendCode("user@example.com", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 코드는 1분에 한 번만 요청할 수 있습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        then(emailVerificationRepository).should(never()).save(any(EmailVerification.class));
        then(mailOutboxService).shouldHaveNoInteractions();
    }

    @Test
    void 인증코드_발송은_출처_기준_한도를_넘으면_저장_없이_거부한다() {
        given(rateLimiter.tryAcquire(eq("email-send:ip:1.2.3.4"), anyInt(), any())).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.sendCode("user@example.com", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        then(emailVerificationRepository).shouldHaveNoInteractions();
        then(mailOutboxService).shouldHaveNoInteractions();
    }

    @Test
    void 인증코드_발송은_전역_총량_한도를_넘으면_저장_없이_거부한다() {
        given(rateLimiter.tryAcquire(eq("email-send:global"), anyInt(), any())).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.sendCode("user@example.com", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        then(emailVerificationRepository).shouldHaveNoInteractions();
        then(mailOutboxService).shouldHaveNoInteractions();
    }

    @Test
    void 인증코드는_1분이_지난_뒤에는_재요청할_수_있다() {
        EmailVerification old = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ReflectionTestUtils.setField(old, "createdAt", LocalDateTime.now().minusMinutes(2));
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(old));

        emailVerificationService.sendCode("user@example.com", "1.2.3.4");

        then(emailVerificationRepository).should().save(any(EmailVerification.class));
    }

    @Test
    void 인증코드_확인_실패가_누적되면_코드를_무효화한다() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ReflectionTestUtils.setField(ev, "attemptCount", 4);
        given(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(ev));

        assertThatThrownBy(() -> emailVerificationService.verifyCode("user@example.com", "999999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 시도 횟수를 초과했습니다. 인증 코드를 다시 요청해 주세요.");

        assertThat(ev.getAttemptCount()).isEqualTo(5);
        assertThat(ev.isUsed()).isTrue();
    }

    @Test
    void 인증코드_확인_실패는_시도_횟수를_증가시킨다() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        given(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(ev));

        assertThatThrownBy(() -> emailVerificationService.verifyCode("user@example.com", "999999"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 코드가 올바르지 않습니다.");

        assertThat(ev.getAttemptCount()).isEqualTo(1);
        assertThat(ev.isUsed()).isFalse();
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
    void 인증코드_확인_성공시_토큰_만료를_코드_만료보다_길게_재부여한다() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        given(emailVerificationRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .willReturn(Optional.of(ev));

        emailVerificationService.verifyCode("user@example.com", "123456");

        // 코드 단계 만료(3분)가 토큰 단계 수명(30분)으로 연장된다
        assertThat(ev.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));
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
        ev.verify("test-token", LocalDateTime.now().plusMinutes(30));
        given(emailVerificationRepository.findByTokenAndUsedFalse("test-token")).willReturn(Optional.of(ev));

        emailVerificationService.validateAndConsume("test-token", "user@example.com");

        assertThat(ev.isUsed()).isTrue();
    }

    @Test
    void 토큰_검증_실패_만료된_토큰은_소비하지_않고_거부한다() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ev.verify("test-token", LocalDateTime.now().minusSeconds(1));
        given(emailVerificationRepository.findByTokenAndUsedFalse("test-token")).willReturn(Optional.of(ev));

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume("test-token", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("만료된 이메일 인증 토큰입니다.");

        assertThat(ev.isUsed()).isFalse();
    }

    @Test
    void 인증된_이메일_조회는_유효한_토큰이면_이메일을_반환한다() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ev.verify("test-token", LocalDateTime.now().plusMinutes(30));
        given(emailVerificationRepository.findByTokenAndUsedFalse("test-token")).willReturn(Optional.of(ev));

        assertThat(emailVerificationService.findVerifiedEmail("test-token")).contains("user@example.com");
    }

    @Test
    void 인증된_이메일_조회는_만료된_토큰이면_빈_값을_반환한다() {
        EmailVerification ev = EmailVerification.create("user@example.com", "123456", LocalDateTime.now().plusMinutes(3));
        ev.verify("test-token", LocalDateTime.now().minusSeconds(1));
        given(emailVerificationRepository.findByTokenAndUsedFalse("test-token")).willReturn(Optional.of(ev));

        assertThat(emailVerificationService.findVerifiedEmail("test-token")).isEmpty();
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
        ev.verify("test-token", LocalDateTime.now().plusMinutes(30));
        given(emailVerificationRepository.findByTokenAndUsedFalse("test-token")).willReturn(Optional.of(ev));

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume("test-token", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 인증 정보가 일치하지 않습니다.");
    }
}
