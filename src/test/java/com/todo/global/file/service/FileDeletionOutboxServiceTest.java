package com.todo.global.file.service;

import com.todo.global.exception.FileStorageException;
import com.todo.global.file.entity.FileDeletionOutbox;
import com.todo.global.file.entity.FileDeletionOutboxStatus;
import com.todo.global.file.event.FileDeletionEnqueuedEvent;
import com.todo.global.file.repository.FileDeletionOutboxRepository;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class FileDeletionOutboxServiceTest {

    @InjectMocks
    private FileDeletionOutboxService fileDeletionOutboxService;

    @Mock
    private FileDeletionOutboxRepository fileDeletionOutboxRepository;
    @Mock
    private FileService fileService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void enqueueAll은_빈키를_제외하고_중복을_제거해_저장한다() {
        AtomicLong ids = new AtomicLong();
        given(fileDeletionOutboxRepository.save(any(FileDeletionOutbox.class))).willAnswer(invocation -> {
            FileDeletionOutbox saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ids.incrementAndGet());
            return saved;
        });

        fileDeletionOutboxService.enqueueAll(Arrays.asList(
                "profiles/1/a.png",
                " ",
                null,
                "proofs/1/b.png",
                "profiles/1/a.png"
        ));

        ArgumentCaptor<FileDeletionOutbox> captor = ArgumentCaptor.forClass(FileDeletionOutbox.class);
        then(fileDeletionOutboxRepository).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FileDeletionOutbox::getObjectKey)
                .containsExactly("profiles/1/a.png", "proofs/1/b.png");
        then(eventPublisher).should().publishEvent(new FileDeletionEnqueuedEvent(1L));
        then(eventPublisher).should().publishEvent(new FileDeletionEnqueuedEvent(2L));
    }

    @Test
    void enqueueAll은_null이면_아무것도_하지_않는다() {
        fileDeletionOutboxService.enqueueAll(null);

        then(fileDeletionOutboxRepository).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void dispatch는_S3_삭제에_성공하면_DELETED로_표시한다() {
        FileDeletionOutbox outbox = pendingOutbox();
        given(fileDeletionOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));

        fileDeletionOutboxService.dispatch(1L);

        then(fileService).should().deleteObjectOrThrow("profiles/1/a.png");
        assertThat(outbox.getStatus()).isEqualTo(FileDeletionOutboxStatus.DELETED);
    }

    @Test
    void dispatch는_S3_삭제_실패시_재시도를_남긴다() {
        FileDeletionOutbox outbox = pendingOutbox();
        given(fileDeletionOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));
        willThrow(new FileStorageException("실패", new IllegalStateException()))
                .given(fileService).deleteObjectOrThrow("profiles/1/a.png");

        fileDeletionOutboxService.dispatch(1L);

        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(FileDeletionOutboxStatus.PENDING);
    }

    @Test
    void dispatch는_이미_처리됐거나_없는_행이면_삭제하지_않는다() {
        FileDeletionOutbox outbox = pendingOutbox();
        outbox.markDeleted();
        given(fileDeletionOutboxRepository.findByIdForUpdate(1L)).willReturn(Optional.of(outbox));
        given(fileDeletionOutboxRepository.findByIdForUpdate(2L)).willReturn(Optional.empty());

        fileDeletionOutboxService.dispatch(1L);
        fileDeletionOutboxService.dispatch(2L);

        then(fileService).should(never()).deleteObjectOrThrow(any());
    }

    private FileDeletionOutbox pendingOutbox() {
        FileDeletionOutbox outbox = FileDeletionOutbox.create(
                "profiles/1/a.png",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(outbox, "id", 1L);
        return outbox;
    }
}
