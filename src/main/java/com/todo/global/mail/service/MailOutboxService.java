package com.todo.global.mail.service;

import com.todo.global.mail.entity.MailOutbox;
import com.todo.global.mail.entity.MailType;
import com.todo.global.mail.event.MailEnqueuedEvent;
import com.todo.global.mail.repository.MailOutboxRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailOutboxService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MailOutboxRepository mailOutboxRepository;
    private final JavaMailSender mailSender;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    /**
     * 호출자 트랜잭션 내에서 메일을 outbox에 적재하고, 커밋 후 즉시 발송을 시도하도록 이벤트를 발행한다.
     * 호출자 트랜잭션이 롤백되면 outbox row도 함께 롤백된다.
     */
    @Transactional
    public void enqueue(
            MailType type,
            String recipient,
            String subject,
            String textBody,
            String htmlBody,
            LocalDateTime expiresAt,
            int maxAttempts
    ) {
        MailOutbox outbox = mailOutboxRepository.save(MailOutbox.create(
                type, recipient, subject, textBody, htmlBody, expiresAt, maxAttempts, LocalDateTime.now(KST)
        ));
        eventPublisher.publishEvent(new MailEnqueuedEvent(outbox.getId()));
    }

    /**
     * 단건 발송을 시도한다. 행에 비관적 락을 걸어 즉시 발송(AFTER_COMMIT)과 폴러의 중복 발송을 막는다.
     */
    @Transactional
    public void dispatch(Long outboxId) {
        MailOutbox outbox = mailOutboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !outbox.isPending()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(KST);
        if (outbox.isExpired(now)) {
            outbox.markExpired();
            return;
        }

        try {
            mailSender.send(buildMessage(outbox));
            outbox.markSent();
        } catch (MailException | MessagingException e) {
            outbox.recordFailure(now);
            log.warn(
                    "MAIL_SMTP_FAILED outboxId={}, type={}, attemptCount={}, status={}, exceptionType={}",
                    outbox.getId(),
                    outbox.getType(),
                    outbox.getAttemptCount(),
                    outbox.getStatus(),
                    e.getClass().getSimpleName()
            );
        }
    }

    private MimeMessage buildMessage(MailOutbox outbox) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        if (fromEmail != null && !fromEmail.isBlank()) {
            helper.setFrom(fromEmail);
        }
        helper.setTo(outbox.getRecipient());
        helper.setSubject(outbox.getSubject());
        helper.setText(outbox.getTextBody(), outbox.getHtmlBody());
        return message;
    }
}
