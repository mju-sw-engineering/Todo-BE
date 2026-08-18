package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.EmailVerification;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.mail.entity.MailType;
import com.todo.global.mail.service.MailOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final int MAIL_MAX_ATTEMPTS = 2;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    // 인증 후 가입 폼 작성·프로필 업로드까지의 여유. 이 안에 가입을 마쳐야 한다.
    private static final Duration VERIFIED_TOKEN_TTL = Duration.ofMinutes(30);

    private final EmailVerificationRepository emailVerificationRepository;
    private final MailOutboxService mailOutboxService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void sendCode(String email) {
        requireResendInterval(email);

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(3);
        EmailVerification ev = EmailVerification.create(email, code, expiresAt);
        emailVerificationRepository.save(ev);

        mailOutboxService.enqueue(
                MailType.VERIFICATION,
                email,
                "[Todo] 이메일 인증 코드",
                buildTextBody(code),
                buildHtmlBody(code),
                expiresAt,
                MAIL_MAX_ATTEMPTS
        );
    }

    private void requireResendInterval(String email) {
        LocalDateTime resendAvailableAfter = LocalDateTime.now().minus(RESEND_INTERVAL);
        boolean requestedTooSoon = emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .map(EmailVerification::getCreatedAt)
                .filter(createdAt -> createdAt.isAfter(resendAvailableAfter))
                .isPresent();
        if (requestedTooSoon) {
            throw new BusinessException("인증 코드는 1분에 한 번만 요청할 수 있습니다.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 실패 시도 횟수는 실패 응답과 함께 남아야 하므로, 검증을 커밋되는 트랜잭션에서 끝내고 예외는 트랜잭션 밖에서 던진다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String verifyCode(String email, String code) {
        VerifyCodeResult result = transactionTemplate.execute(status -> verifyCodeInTransaction(email, code));
        result.throwIfFailed();
        return result.token();
    }

    private VerifyCodeResult verifyCodeInTransaction(String email, String code) {
        EmailVerification ev = emailVerificationRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElse(null);
        if (ev == null) {
            return VerifyCodeResult.failed("이메일 인증 요청이 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (ev.isExpired()) {
            return VerifyCodeResult.failed("인증 코드가 만료되었습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!ev.getCode().equals(code)) {
            ev.increaseAttempt();
            if (ev.isAttemptExceeded(MAX_VERIFY_ATTEMPTS)) {
                ev.markAsUsed();
                return VerifyCodeResult.failed(
                        "인증 시도 횟수를 초과했습니다. 인증 코드를 다시 요청해 주세요.", HttpStatus.BAD_REQUEST);
            }
            return VerifyCodeResult.failed("인증 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        String token = UUID.randomUUID().toString();
        ev.verify(token, LocalDateTime.now().plus(VERIFIED_TOKEN_TTL));
        return VerifyCodeResult.success(token);
    }

    private record VerifyCodeResult(String token, BusinessException failure) {
        static VerifyCodeResult success(String token) {
            return new VerifyCodeResult(token, null);
        }

        static VerifyCodeResult failed(String message, HttpStatus status) {
            return new VerifyCodeResult(null, new BusinessException(message, status));
        }

        void throwIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    /**
     * 토큰을 소비하지 않고 인증된 이메일만 조회한다.
     *
     * 회원가입이 끝나기 전(프로필 사진 업로드 시점)에 요청자를 식별하려면 토큰이 필요한데,
     * 여기서 {@link #validateAndConsume}을 쓰면 뒤이은 회원가입이 토큰 없음으로 실패한다.
     */
    public Optional<String> findVerifiedEmail(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return emailVerificationRepository.findByTokenAndUsedFalse(token)
                .filter(ev -> !ev.isExpired())
                .map(EmailVerification::getEmail);
    }

    @Transactional
    public void validateAndConsume(String token, String email) {
        EmailVerification ev = emailVerificationRepository
                .findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new BusinessException("유효하지 않은 이메일 인증 토큰입니다.", HttpStatus.BAD_REQUEST));

        if (ev.isExpired()) {
            throw new BusinessException("만료된 이메일 인증 토큰입니다.", HttpStatus.BAD_REQUEST);
        }
        if (!ev.getEmail().equals(email)) {
            throw new BusinessException("이메일 인증 정보가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        ev.markAsUsed();
    }

    private String buildTextBody(String code) {
        return "인증 코드: " + code + "\n3분 이내에 입력해 주세요.";
    }

    private String buildHtmlBody(String code) {
        return """
                <!doctype html>
                <html lang="ko">
                <body style="margin:0;padding:0;background:#f6f4ff;font-family:Arial,sans-serif;color:#1f2937;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:36px 16px;">
                        <tr><td align="center">
                            <table role="presentation" style="max-width:480px;background:#fff;border:1px solid #ebe7ff;border-radius:20px;overflow:hidden;">
                                <tr><td style="background:#5B4FCF;padding:24px 32px;color:#fff;">
                                    <h2 style="margin:0;font-size:22px;">[Todo] 이메일 인증</h2>
                                </td></tr>
                                <tr><td style="padding:32px;">
                                    <p style="margin:0 0 16px;font-size:15px;">아래 인증 코드를 입력해 주세요. (3분 이내)</p>
                                    <div style="padding:20px;background:#f1efff;border-radius:14px;text-align:center;font-size:32px;font-weight:900;letter-spacing:6px;color:#312e81;">%s</div>
                                </td></tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(code);
    }
}
