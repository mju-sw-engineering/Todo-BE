package com.todo.global.mail.service;

import com.todo.global.mail.entity.MailOutbox;
import com.todo.global.mail.entity.MailOutboxStatus;
import com.todo.global.mail.entity.MailType;
import com.todo.global.mail.event.MailEnqueuedEvent;
import com.todo.global.mail.repository.MailOutboxRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MailOutboxServiceTest {

    @InjectMocks
    private MailOutboxService mailOutboxService;

    @Mock
    private MailOutboxRepository mailOutboxRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void enqueue는_outbox를_저장하고_이벤트를_발행한다() {
        given(mailOutboxRepository.save(any(MailOutbox.class))).willAnswer(inv -> {
            MailOutbox saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        mailOutboxService.enqueue(MailType.VERIFICATION, "user@example.com", "제목", "본문", "<p>본문</p>",
                LocalDateTime.now().plusMinutes(3), 2);

        then(mailOutboxRepository).should().save(any(MailOutbox.class));
        then(eventPublisher).should().publishEvent(new MailEnqueuedEvent(7L));
    }

    @Test
    void dispatch는_발송에_성공하면_SENT로_표시한다() {
        MailOutbox outbox = pendingOutbox(null, 3);
        given(mailOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));
        given(mailSender.createMimeMessage()).willReturn(mock(MimeMessage.class));

        mailOutboxService.dispatch(1L);

        assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.SENT);
        then(mailSender).should().send(any(MimeMessage.class));
    }

    @Test
    void dispatch는_만료된_메일을_발송하지_않고_FAILED로_종결한다() {
        MailOutbox outbox = pendingOutbox(LocalDateTime.now().minusMinutes(1), 3);
        given(mailOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));

        mailOutboxService.dispatch(1L);

        assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.FAILED);
        then(mailSender).should(never()).send(any(MimeMessage.class));
    }

    @Test
    void dispatch는_발송_실패시_재시도를_남기고_최대시도_초과시_FAILED로_종결한다() {
        MailOutbox outbox = pendingOutbox(null, 1);
        given(mailOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));
        given(mailSender.createMimeMessage()).willReturn(mock(MimeMessage.class));
        willThrow(new MailSendException("smtp error")).given(mailSender).send(any(MimeMessage.class));

        mailOutboxService.dispatch(1L);

        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.FAILED);
    }

    @Test
    void dispatch는_발송_실패시_재시도가_남으면_PENDING을_유지한다() {
        MailOutbox outbox = pendingOutbox(null, 3);
        given(mailOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));
        given(mailSender.createMimeMessage()).willReturn(mock(MimeMessage.class));
        willThrow(new MailSendException("smtp error")).given(mailSender).send(any(MimeMessage.class));

        mailOutboxService.dispatch(1L);

        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
    }

    @Test
    void dispatch는_이미_처리된_메일이면_아무것도_하지_않는다() {
        MailOutbox outbox = pendingOutbox(null, 3);
        outbox.markSent();
        given(mailOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));

        mailOutboxService.dispatch(1L);

        then(mailSender).should(never()).send(any(MimeMessage.class));
    }

    private MailOutbox pendingOutbox(LocalDateTime expiresAt, int maxAttempts) {
        return MailOutbox.create(MailType.VERIFICATION, "user@example.com", "제목", "본문", "<p>본문</p>",
                expiresAt, maxAttempts, LocalDateTime.now());
    }
}
