package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.AppleRevokeOutbox;
import com.todo.domain.auth.entity.AppleRevokeOutboxStatus;
import com.todo.domain.auth.repository.AppleRevokeOutboxRepository;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppleRevokeOutboxServiceTest {

    @InjectMocks
    private AppleRevokeOutboxService appleRevokeOutboxService;

    @Mock
    private AppleRevokeOutboxRepository appleRevokeOutboxRepository;
    @Mock
    private AppleTokenClient appleTokenClient;

    @Test
    void enqueue는_PENDING_상태로_저장한다() {
        appleRevokeOutboxService.enqueue(1L, "apple-rt", "com.test.app");

        ArgumentCaptor<AppleRevokeOutbox> captor = ArgumentCaptor.forClass(AppleRevokeOutbox.class);
        then(appleRevokeOutboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getAppleRefreshToken()).isEqualTo("apple-rt");
        assertThat(captor.getValue().getAppleClientId()).isEqualTo("com.test.app");
        assertThat(captor.getValue().getStatus()).isEqualTo(AppleRevokeOutboxStatus.PENDING);
    }

    @Test
    void dispatch는_revoke에_성공하면_REVOKED로_표시한다() {
        AppleRevokeOutbox outbox = pendingOutbox();
        given(appleRevokeOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));

        appleRevokeOutboxService.dispatch(1L);

        then(appleTokenClient).should().revokeRefreshToken("apple-rt", "com.test.app");
        assertThat(outbox.getStatus()).isEqualTo(AppleRevokeOutboxStatus.REVOKED);
    }

    @Test
    void dispatch는_revoke_실패시_재시도_기록을_남긴다() {
        AppleRevokeOutbox outbox = pendingOutbox();
        given(appleRevokeOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));
        willThrow(new BusinessException("실패", HttpStatus.BAD_REQUEST))
                .given(appleTokenClient).revokeRefreshToken("apple-rt", "com.test.app");

        appleRevokeOutboxService.dispatch(1L);

        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(AppleRevokeOutboxStatus.PENDING);
    }

    @Test
    void dispatch는_이미_처리됐거나_없는_행이면_아무것도_하지_않는다() {
        AppleRevokeOutbox outbox = pendingOutbox();
        outbox.markRevoked();
        given(appleRevokeOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));
        given(appleRevokeOutboxRepository.findByIdForUpdate(2L)).willReturn(Optional.empty());

        appleRevokeOutboxService.dispatch(1L);
        appleRevokeOutboxService.dispatch(2L);

        then(appleTokenClient).should(never()).revokeRefreshToken(any(), any());
    }

    private AppleRevokeOutbox pendingOutbox() {
        AppleRevokeOutbox outbox = AppleRevokeOutbox.create(1L, "apple-rt", "com.test.app", LocalDateTime.now());
        ReflectionTestUtils.setField(outbox, "id", 1L);
        return outbox;
    }
}
