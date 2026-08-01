package com.todo.global.file.scheduler;

import com.todo.global.file.entity.FileDeletionOutboxStatus;
import com.todo.global.file.repository.FileDeletionOutboxRepository;
import com.todo.global.file.service.FileDeletionOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class FileDeletionOutboxPollerTest {

    @InjectMocks
    private FileDeletionOutboxPoller poller;

    @Mock
    private FileDeletionOutboxRepository repository;
    @Mock
    private FileDeletionOutboxService service;

    @Test
    void 재시도_대상_파일을_각각_삭제한다() {
        given(repository.findDispatchableIds(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(1L, 2L));

        poller.retryPending();

        then(service).should().dispatch(1L);
        then(service).should().dispatch(2L);
    }

    @Test
    void 한건_삭제가_실패해도_나머지를_계속_처리한다() {
        given(repository.findDispatchableIds(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("DB 오류")).given(service).dispatch(1L);

        poller.retryPending();

        then(service).should().dispatch(2L);
    }

    @Test
    void 보존기간이_지난_처리완료_행을_정리한다() {
        given(repository.deleteByStatusInAndUpdatedAtBefore(
                eq(List.of(FileDeletionOutboxStatus.DELETED)),
                any(LocalDateTime.class)
        )).willReturn(3);

        poller.cleanupProcessed();

        then(repository).should().deleteByStatusInAndUpdatedAtBefore(
                eq(List.of(FileDeletionOutboxStatus.DELETED)),
                any(LocalDateTime.class)
        );
    }
}
