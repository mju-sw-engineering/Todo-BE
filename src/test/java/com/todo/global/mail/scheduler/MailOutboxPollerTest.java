package com.todo.global.mail.scheduler;

import com.todo.global.mail.entity.MailOutboxStatus;
import com.todo.global.mail.repository.MailOutboxRepository;
import com.todo.global.mail.service.MailOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MailOutboxPollerTest {

    @InjectMocks
    private MailOutboxPoller mailOutboxPoller;

    @Mock
    private MailOutboxRepository mailOutboxRepository;
    @Mock
    private MailOutboxService mailOutboxService;

    @Test
    void 재시도_대상_메일을_각각_발송한다() {
        given(mailOutboxRepository.findDispatchableIds(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(1L, 2L, 3L));

        mailOutboxPoller.retryPending();

        then(mailOutboxService).should().dispatch(1L);
        then(mailOutboxService).should().dispatch(2L);
        then(mailOutboxService).should().dispatch(3L);
    }

    @Test
    void 한_건_발송이_실패해도_나머지를_계속_발송한다() {
        given(mailOutboxRepository.findDispatchableIds(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(1L, 2L));
        org.mockito.BDDMockito.willThrow(new RuntimeException("boom")).given(mailOutboxService).dispatch(1L);

        mailOutboxPoller.retryPending();

        then(mailOutboxService).should().dispatch(2L);
    }

    @Test
    void 보존기간이_지난_처리완료_메일을_정리한다() {
        given(mailOutboxRepository.deleteByStatusInAndUpdatedAtBefore(
                eq(List.of(MailOutboxStatus.SENT, MailOutboxStatus.FAILED)), any(LocalDateTime.class)))
                .willReturn(5);

        mailOutboxPoller.cleanupProcessed();

        then(mailOutboxRepository).should().deleteByStatusInAndUpdatedAtBefore(
                eq(List.of(MailOutboxStatus.SENT, MailOutboxStatus.FAILED)), any(LocalDateTime.class));
    }
}
