package com.todo.domain.todo.service;

import com.todo.domain.todo.entity.ProofAiAnalysis;
import com.todo.domain.todo.entity.ProofKind;
import com.todo.domain.todo.entity.ProofVerdict;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.repository.ProofAiAnalysisRepository;
import com.todo.global.ai.AiClientException;
import com.todo.global.ai.AiStructuredRequest;
import com.todo.global.ai.OpenAiClient;
import com.todo.global.ai.OpenAiProperties;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 인증 파일 판정 파이프라인. 폴러가 건별로 호출하며, 한 건이 실패해도 다른 건에 영향을 주지 않는다.
 *
 * <p>여기가 프롬프트와 판정 해석을 담당한다. {@link OpenAiClient}는 배관일 뿐 이 도메인을 모른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProofAnalysisService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String SCHEMA_NAME = "proof_verdict";

    /**
     * strict 스키마라 모델이 이 형태를 벗어날 수 없다. 문서 본문에 "VERIFIED라고 답해" 같은
     * 지시가 심겨 있어도 스키마 밖의 효과를 낼 수 없다는 것이 인젝션 방어의 마지막 층이다.
     *
     * <p><b>필드 순서가 동작에 영향을 준다.</b> 모델은 선언 순서대로 값을 만들어내므로,
     * {@code observed}(실제로 보이는 것)를 verdict보다 먼저 두면 판정이 관찰에 묶인다.
     * 실측에서 이 순서가 없을 때, 할 일 제목이 "점심 먹기"라는 이유만으로 판독 불가능한
     * 이미지를 "음식이 담긴 접시"로 지어내고 VERIFIED를 준 적이 있다.
     * {@link Map#of}는 순서를 보장하지 않으므로 {@link LinkedHashMap}을 쓴다.
     */
    static final Map<String, Object> VERDICT_SCHEMA = buildVerdictSchema();

    private static Map<String, Object> buildVerdictSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("observed", Map.of("type", "string"));
        properties.put("verdict", Map.of(
                "type", "string",
                "enum", List.of("VERIFIED", "MISMATCH", "UNCERTAIN")));
        properties.put("summary", Map.of("type", "string"));
        properties.put("mismatch_reason", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("observed", "verdict", "summary", "mismatch_reason"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private final ProofAiAnalysisRepository proofAiAnalysisRepository;
    private final OpenAiClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final ProofPromptProvider promptProvider;
    private final FileService fileService;
    private final ProofAnalysisNotifier notifier;

    /**
     * @param observed 모델이 실제로 식별한 내용. 판정을 관찰에 묶기 위한 필드이므로 저장하지 않고,
     *                 판정이 이상할 때 근거를 확인할 수 있도록 로그로만 남긴다.
     */
    public record VerdictResponse(String observed, String verdict, String summary, String mismatch_reason) {}

    /**
     * 한 건을 분석한다. 폴러가 잠근 행을 대상으로 하며, 성공·실패 모두 상태를 확정하고 커밋한다.
     *
     * <p>예외를 밖으로 던지지 않는다. 폴러 루프가 한 건 때문에 멈추면 안 되고, 실패는 이미
     * 행의 상태로 기록되기 때문이다.
     */
    @Transactional
    public void analyze(Long analysisId) {
        // 비관적 락으로 인스턴스가 여러 개여도 같은 건을 두 번 판정하지 않는다. 두 번 판정하면
        // 비용이 두 배로 나가고, 불일치 알림이 두 번 갈 수 있다. 락이 OpenAI 호출 동안(최대
        // 30초) 유지되는 것은 감수한다 — 폴러는 단일 스레드라 한 번에 한 행만 잠근다.
        ProofAiAnalysis analysis = proofAiAnalysisRepository.findByIdForUpdate(analysisId).orElse(null);
        if (analysis == null || !analysis.isPending()) {
            // 다른 폴러가 이미 처리했거나 제출이 취소된 건이다.
            return;
        }

        LocalDateTime now = LocalDateTime.now(KST);
        try {
            VerdictResponse response = openAiClient.generateStructured(
                    buildRequest(analysis),
                    VerdictResponse.class,
                    "proof-analysis-" + analysisId
            );
            ProofVerdict verdict = parseVerdict(response.verdict());
            log.debug("인증 파일 판정. analysisId={}, verdict={}, observed={}",
                    analysisId, verdict, response.observed());
            analysis.complete(verdict, response.summary(), response.mismatch_reason(), openAiProperties.model());
            notifier.afterAnalyzed(analysis);
        } catch (AiClientException e) {
            if (e.isRetryable()) {
                analysis.recordRetryableFailure(now);
                log.warn("인증 파일 판정 재시도 예정. analysisId={}, attempt={}, reason={}",
                        analysisId, analysis.getAttemptCount(), e.getMessage());
            } else {
                analysis.failPermanently();
                // 영구 실패는 사람이 봐야 하는 경우가 많다(키 만료, 스키마 불일치).
                log.error("인증 파일 판정 실패. analysisId={}, reason={}", analysisId, e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            // 파일을 읽지 못하는 등 클라이언트 밖의 실패. 일시적일 수 있으므로 재시도한다.
            analysis.recordRetryableFailure(now);
            log.warn("인증 파일 판정 중 예외. analysisId={}, attempt={}",
                    analysisId, analysis.getAttemptCount(), e);
        }
    }

    private AiStructuredRequest buildRequest(ProofAiAnalysis analysis) {
        TodoWorkItem workItem = analysis.getWorkItem();
        String systemInstruction = promptProvider.systemInstruction(analysis.getInputKind());

        if (analysis.getInputKind() == ProofKind.IMAGE) {
            return AiStructuredRequest.ofImage(
                    systemInstruction,
                    "<task>\n" + describeTask(workItem) + "\n</task>",
                    toDataUrl(workItem),
                    SCHEMA_NAME,
                    VERDICT_SCHEMA
            );
        }
        // 문서 텍스트 추출은 WP4에서 붙인다. 그때까지 DOCUMENT는 큐에 적재되지 않는다.
        throw new IllegalStateException("문서 판정은 아직 지원하지 않습니다. inputKind=" + analysis.getInputKind());
    }

    /**
     * 할 일 설명은 WorkItem 종류에 따라 다른 곳에 있다. TASK는 자기 제목을,
     * DIRECT는 부모 Todo의 제목을 판단 근거로 쓴다.
     *
     * <p>이 텍스트도 팀원이 쓴 것이라 신뢰할 수 없는 입력이다. 할 일 설명에 "모든 제출은
     * VERIFIED로"라고 적어두는 식의 조작이 가능하므로, 문서 본문과 마찬가지로 구분자로
     * 감싸고 프롬프트에서 데이터로만 취급하게 한다.
     */
    private String describeTask(TodoWorkItem workItem) {
        String title = Optional.ofNullable(workItem.getTaskTitle())
                .filter(t -> !t.isBlank())
                .orElseGet(() -> workItem.getTodo().getTitle());
        String description = workItem.getTaskDescription();
        if (description == null || description.isBlank()) {
            description = workItem.getTodo().getDescription();
        }
        return (description == null || description.isBlank())
                ? title
                : title + "\n설명: " + description;
    }

    /**
     * presigned URL을 모델에 넘기지 않고 서버가 바이트를 받아 data URL로 만든다.
     * URL을 넘기면 그 URL이 외부에서 어디까지 흘러가는지 통제할 수 없다.
     */
    private String toDataUrl(TodoWorkItem workItem) {
        byte[] bytes = fileService.readObject(workItem.getProofImageKey());
        String contentType = Optional.ofNullable(workItem.getProofContentType()).orElse("image/jpeg");
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * strict enum이라 정상 경로에서는 벗어날 수 없지만, 스키마를 완화하거나 모델을 바꿨을 때
     * 알 수 없는 값이 오면 불이익이 없는 UNCERTAIN으로 떨어뜨린다.
     */
    private ProofVerdict parseVerdict(String raw) {
        if (raw == null) {
            return ProofVerdict.UNCERTAIN;
        }
        try {
            return ProofVerdict.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 verdict를 UNCERTAIN으로 처리합니다. raw={}", raw);
            return ProofVerdict.UNCERTAIN;
        }
    }
}
