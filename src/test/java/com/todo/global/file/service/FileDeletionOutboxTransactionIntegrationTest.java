package com.todo.global.file.service;

import com.todo.global.file.repository.FileDeletionOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;

@SpringBootTest
@ActiveProfiles("test")
class FileDeletionOutboxTransactionIntegrationTest {

    @Autowired
    private FileDeletionOutboxService fileDeletionOutboxService;

    @Autowired
    private FileDeletionOutboxRepository fileDeletionOutboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private FileDeletionAsyncDispatcher fileDeletionAsyncDispatcher;

    @AfterEach
    void tearDown() {
        fileDeletionOutboxRepository.deleteAll();
    }

    @Test
    void 파일삭제는_적재_트랜잭션이_커밋된_뒤에만_요청한다() {
        transactionTemplate.executeWithoutResult(status -> {
            fileDeletionOutboxService.enqueueAll(List.of("profiles/1/a.png"));

            then(fileDeletionAsyncDispatcher).shouldHaveNoInteractions();
        });

        assertThat(fileDeletionOutboxRepository.findAll()).hasSize(1);
        then(fileDeletionAsyncDispatcher).should().dispatch(anyLong());
    }

    @Test
    void 적재_트랜잭션이_롤백되면_outbox와_삭제요청이_모두_남지_않는다() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            fileDeletionOutboxService.enqueueAll(List.of("profiles/1/a.png"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fileDeletionOutboxRepository.findAll()).isEmpty();
        then(fileDeletionAsyncDispatcher).shouldHaveNoInteractions();
    }
}
