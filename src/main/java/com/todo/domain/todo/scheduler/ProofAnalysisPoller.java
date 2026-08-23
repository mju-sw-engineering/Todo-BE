package com.todo.domain.todo.scheduler;

import com.todo.domain.todo.repository.ProofAiAnalysisRepository;
import com.todo.domain.todo.service.ProofAnalysisService;
import com.todo.global.ai.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 분석 대기 중인 인증 파일을 집어 판정을 돌린다. {@code AppleRevokeOutboxPoller}와 같은 구조다.
 *
 * <p>제출 요청 경로에서 OpenAI를 부르지 않는 이유가 여기 있다. 판정은 몇 초가 걸리고
 * 외부 장애에 영향받는데, 그걸 제출 응답에 물리면 OpenAI가 느려질 때 제출 자체가 느려진다.
 * 큐에 넣고 돌아서면 OpenAI가 죽어도 제출은 정상 동작하고 뱃지만 늦게 붙는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProofAnalysisPoller {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProofAiAnalysisRepository proofAiAnalysisRepository;
    private final ProofAnalysisService proofAnalysisService;
    private final OpenAiProperties openAiProperties;

    @Value("${proof-analysis.batch-size:20}")
    private int batchSize;

    /**
     * 스케줄러 전체가 아니라 AI 폴러만 끌 수 있어야 한다. OpenAI 장애가 길어지면 이 스위치로
     * 호출을 멈추고, 복구 후 다시 켜면 큐에 쌓인 건이 그대로 처리된다.
     */
    @Value("${proof-analysis.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${proof-analysis.poll-interval-ms:10000}")
    public void analyzePending() {
        if (!enabled) {
            return;
        }
        if (openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank()) {
            // 키 없이 처리하면 모든 건이 영구 FAILED로 확정돼, 나중에 키를 넣어도 복구되지 않는다.
            // 건너뛰고 PENDING으로 두면 키가 설정되는 순간 밀린 큐가 그대로 처리된다.
            log.warn("OPENAI_API_KEY가 없어 인증 파일 판정을 건너뜁니다. 큐는 유지됩니다.");
            return;
        }

        List<Long> ids = proofAiAnalysisRepository.findDispatchableIds(
                LocalDateTime.now(KST),
                PageRequest.of(0, batchSize)
        );
        for (Long id : ids) {
            try {
                proofAnalysisService.analyze(id);
            } catch (RuntimeException e) {
                // 서비스가 자체적으로 상태를 확정하지만, 저장 자체가 실패하는 경우가 남는다.
                // 한 건 때문에 나머지 배치가 멈추면 안 된다.
                log.warn("인증 파일 판정 처리 중 예외. analysisId={}", id, e);
                // 확정이 롤백됐다면 PENDING 그대로라 다음 주기에 다시 유료 호출이 나간다.
                // 별도 트랜잭션에서 횟수를 올려 반복이 결국 FAILED로 멎게 한다.
                recordDispatchFailureQuietly(id);
            }
        }
    }

    private void recordDispatchFailureQuietly(Long analysisId) {
        try {
            proofAnalysisService.recordDispatchFailure(analysisId);
        } catch (RuntimeException e) {
            // 여기까지 실패하면 다음 주기에 다시 시도된다. 배치를 멈추지는 않는다.
            log.warn("재시도 횟수 기록에 실패했습니다. analysisId={}", analysisId, e);
        }
    }
}
