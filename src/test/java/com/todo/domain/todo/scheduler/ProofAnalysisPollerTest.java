package com.todo.domain.todo.scheduler;

import com.todo.domain.todo.repository.ProofAiAnalysisRepository;
import com.todo.domain.todo.service.ProofAnalysisService;
import com.todo.global.ai.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProofAnalysisPollerTest {

    @InjectMocks
    private ProofAnalysisPoller poller;

    @Mock
    private ProofAiAnalysisRepository proofAiAnalysisRepository;
    @Mock
    private ProofAnalysisService proofAnalysisService;
    @Mock
    private OpenAiProperties openAiProperties;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(poller, "enabled", true);
        ReflectionTestUtils.setField(poller, "batchSize", 20);
        lenient().when(openAiProperties.apiKey()).thenReturn("test-key");
    }

    @Test
    void API_키가_없으면_큐를_건드리지_않고_건너뛴다() {
        // 키 없이 처리하면 전부 영구 FAILED가 돼 나중에 키를 넣어도 복구되지 않는다.
        given(openAiProperties.apiKey()).willReturn("  ");

        poller.analyzePending();

        then(proofAiAnalysisRepository).shouldHaveNoInteractions();
        then(proofAnalysisService).should(never()).analyze(anyLong());
    }

    @Test
    void 처리_대상을_배치로_집어_건별로_분석한다() {
        given(proofAiAnalysisRepository.findDispatchableIds(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(1L, 2L, 3L));

        poller.analyzePending();

        then(proofAnalysisService).should().analyze(1L);
        then(proofAnalysisService).should().analyze(2L);
        then(proofAnalysisService).should().analyze(3L);
    }

    @Test
    void 한_건이_터져도_나머지_배치는_계속_처리한다() {
        given(proofAiAnalysisRepository.findDispatchableIds(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("저장 실패")).given(proofAnalysisService).analyze(2L);

        poller.analyzePending();

        then(proofAnalysisService).should().analyze(1L);
        then(proofAnalysisService).should().analyze(3L);
    }

    @Test
    void 스위치를_끄면_조회조차_하지_않는다() {
        // OpenAI 장애가 길어질 때 스케줄러 전체가 아니라 이 폴러만 멈출 수 있어야 한다.
        ReflectionTestUtils.setField(poller, "enabled", false);

        poller.analyzePending();

        then(proofAiAnalysisRepository).shouldHaveNoInteractions();
        then(proofAnalysisService).should(never()).analyze(anyLong());
    }
}
