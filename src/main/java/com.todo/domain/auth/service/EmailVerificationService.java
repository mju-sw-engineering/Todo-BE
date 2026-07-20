package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.EmailVerification;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.global.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Transactional
    public void sendCode(String email) {
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        EmailVerification ev = EmailVerification.create(email, code, LocalDateTime.now().plusMinutes(3));
        emailVerificationRepository.save(ev);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (fromEmail != null && !fromEmail.isBlank()) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(email);
            helper.setSubject("[Todo] 이메일 인증 코드");
            helper.setText(buildTextBody(code), buildHtmlBody(code));
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            throw new BusinessException("인증 메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public String verifyCode(String email, String code) {
        EmailVerification ev = emailVerificationRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BusinessException("이메일 인증 요청이 없습니다.", HttpStatus.BAD_REQUEST));

        if (ev.isExpired()) {
            throw new BusinessException("인증 코드가 만료되었습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!ev.getCode().equals(code)) {
            throw new BusinessException("인증 코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        String token = UUID.randomUUID().toString();
        ev.verify(token);
        return token;
    }

    @Transactional
    public void validateAndConsume(String token, String email) {
        EmailVerification ev = emailVerificationRepository
                .findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new BusinessException("유효하지 않은 이메일 인증 토큰입니다.", HttpStatus.BAD_REQUEST));

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
