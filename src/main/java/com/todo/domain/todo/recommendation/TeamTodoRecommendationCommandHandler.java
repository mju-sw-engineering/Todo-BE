package com.todo.domain.todo.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.SlashCommandHandler;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationItem;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationResult;
import com.todo.domain.user.entity.User;
import com.todo.global.ai.AiClientException;
import com.todo.global.ai.AiStructuredRequest;
import com.todo.global.ai.OpenAiClient;
import com.todo.global.ai.OpenAiProperties;
import com.todo.global.ratelimit.SimpleRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * `/할일추천` — 팀 활동을 요약해 모델에 묻고 등록 가능한 카드로 돌려준다.
 *
 * <p><b>거부할 수 없는 자리다.</b> 명령어 인프라의 핸들러 계약은 결과를 돌려주거나 예외를 던지는
 * 것뿐이고, 예외는 FAILED(= "처리하지 못했어요")가 된다. 그래서 rate limit·데이터 없음·기능
 * 꺼짐처럼 <i>정상적으로 아무것도 안 하는</i> 경우는 예외가 아니라 {@link RecommendationOutcome}
 * 으로 표현한다. 사용자에게는 이유가 담긴 카드가 보인다.
 *
 * <p>이 메서드는 인프라의 비동기 스레드에서, 인프라가 연 트랜잭션 안에서 실행된다. OpenAI 호출
 * 동안 DB 커넥션 하나를 잡고 있으므로 스레드 풀 상한(2)이 곧 점유 커넥션 상한이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamTodoRecommendationCommandHandler implements SlashCommandHandler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_OUTPUT_TOKENS = 1200;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);

    private final TeamActivityDigestBuilder digestBuilder;
    private final RecommendationPromptProvider promptProvider;
    private final RecommendationResultSanitizer sanitizer;
    private final OpenAiClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final TodoRecommendationProperties properties;
    private final SimpleRateLimiter rateLimiter;
    private final SlashCommandExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public SlashCommand command() {
        return SlashCommand.TODO_RECOMMENDATION;
    }

    @Override
    public Object execute(Team team, User executor) {
        if (!properties.isEnabled() || !hasApiKey()) {
            log.info("추천 요청을 처리하지 않습니다. teamId={}, enabled={}, hasKey={}",
                    team.getId(), properties.isEnabled(), hasApiKey());
            return TeamTodoRecommendationResult.unavailable();
        }

        Optional<Long> recent = recentResultMessageId(team.getId());
        if (recent.isPresent()) {
            return TeamTodoRecommendationResult.cooldown(recent.get());
        }
        if (!acquirePermits(team.getId())) {
            return TeamTodoRecommendationResult.cooldown(lastResultMessageId(team.getId()).orElse(null));
        }

        LocalDate today = LocalDate.now(clock.withZone(KST));
        TeamActivityDigest digest = digestBuilder.build(team.getId(), today);
        if (digest.mode() == RecommendationMode.NONE) {
            return TeamTodoRecommendationResult.none();
        }

        AiRecommendationResponse response = callModel(digest, team.getId());
        List<TeamTodoRecommendationItem> items = sanitizer.sanitize(response, digest);
        log.info("팀 할 일 추천 완료. teamId={}, mode={}, items={}", team.getId(), digest.mode(), items.size());
        log.debug("추천 근거. teamId={}, observations={}", team.getId(), response.observations());

        return digest.mode() == RecommendationMode.STARTER
                ? TeamTodoRecommendationResult.starter(response.greeting(), items)
                : TeamTodoRecommendationResult.ready(response.greeting(), items);
    }

    /**
     * 재시도는 한 번뿐이다. 채팅에서 기다리는 사람이 있으므로 긴 재시도보다 빠른 실패 안내가 낫다.
     * 여기서 던진 예외는 인프라가 FAILED로 확정한다.
     */
    private AiRecommendationResponse callModel(TeamActivityDigest digest, Long teamId) {
        AiStructuredRequest request = new AiStructuredRequest(
                promptProvider.systemInstruction(digest.mode()),
                promptProvider.userText(digest),
                null,
                RecommendationPromptProvider.SCHEMA_NAME,
                promptProvider.schema(),
                MAX_OUTPUT_TOKENS
        );
        String clientRequestId = "team-reco-" + teamId + "-" + digest.today();
        try {
            return openAiClient.generateStructured(request, AiRecommendationResponse.class, clientRequestId);
        } catch (AiClientException e) {
            if (!e.isRetryable()) {
                throw e;
            }
            log.warn("추천 생성 재시도. teamId={}, reason={}", teamId, e.getMessage());
            if (!sleepBeforeRetry()) {
                log.warn("종료 중이라 추천 재시도를 포기합니다. teamId={}", teamId);
                throw e;
            }
            return openAiClient.generateStructured(request, AiRecommendationResponse.class, clientRequestId);
        }
    }

    /**
     * 백오프 대기. 인터럽트가 오면 셧다운 중이라는 뜻이므로 {@code false}를 돌려 재시도를 막는다.
     * 인터럽트 플래그만 복원하고 재시도를 진행하면, 이미 끊긴 스레드로 30초짜리 HTTP 호출을
     * 새로 시작해 종료를 그만큼 더 끌고 실행 행은 PENDING으로 남는다.
     *
     * @return 정상적으로 다 기다렸으면 true, 인터럽트로 끊겼으면 false
     */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean hasApiKey() {
        return openAiProperties.apiKey() != null && !openAiProperties.apiKey().isBlank();
    }

    /**
     * 쿨다운 안에 이미 결과가 있으면 모델을 부르지 않고 그 카드를 가리킨다. 팀원 여럿이 연달아
     * 명령어를 쳐도 같은 데이터로 같은 답을 다시 사지 않는다.
     */
    private Optional<Long> recentResultMessageId(Long teamId) {
        return lastResult(teamId)
                .filter(execution -> execution.getExecutedAt() != null)
                .filter(execution -> execution.getExecutedAt()
                        .isAfter(java.time.LocalDateTime.now(clock.withZone(KST)).minus(properties.cooldown())))
                .map(execution -> execution.getChatMessage().getId());
    }

    private Optional<Long> lastResultMessageId(Long teamId) {
        return lastResult(teamId).map(execution -> execution.getChatMessage().getId());
    }

    /**
     * 최근 실행을 새 것부터 훑어 카드가 담긴 첫 결과를 찾는다. 한 건만 보면 COOLDOWN 결과가
     * 바로 앞의 READY 카드를 가려, 세 번째 호출자부터는 10분 쿨다운이 통째로 무시된다.
     *
     * <p>훑는 범위를 넘도록 안내 결과만 쌓이면 직전 카드를 못 찾지만, 그때는 rate limit이
     * 다음 방어선으로 남는다 — 20건은 사람이 채팅에서 낼 수 있는 속도를 충분히 덮는다.
     */
    private Optional<SlashCommandExecution> lastResult(Long teamId) {
        return executionRepository
                .findTop20ByTeamIdAndCommandOrderByIdDesc(teamId, SlashCommand.TODO_RECOMMENDATION)
                .stream()
                .filter(execution -> execution.getResultJson() != null)
                .filter(execution -> !isSkippedResult(execution))
                .findFirst();
    }

    /**
     * 안내만 담긴 결과(COOLDOWN·UNAVAILABLE)는 "직전 추천"이 아니다. 그것을 가리키면 사용자가
     * 안내를 눌러 또 안내를 보게 된다.
     */
    private boolean isSkippedResult(SlashCommandExecution execution) {
        try {
            RecommendationOutcome outcome = objectMapper
                    .readValue(execution.getResultJson(), TeamTodoRecommendationResult.class)
                    .outcome();
            return outcome == RecommendationOutcome.COOLDOWN || outcome == RecommendationOutcome.UNAVAILABLE;
        } catch (Exception e) {
            // 다른 형식이거나 깨진 결과다. 쿨다운 판정에서 제외하는 편이 안전하다.
            return true;
        }
    }

    /**
     * 두 한도를 모두 통과해야 한다. 짧은 창은 연타를, 하루 창은 하루치 비용을 막는다.
     * 짧은 창을 통과한 뒤 하루 한도에서 막히면 이미 쓴 토큰은 돌려받지 못하지만, 한도 초과
     * 상황에서 몇 건 더 세는 것은 문제가 되지 않는다.
     */
    private boolean acquirePermits(Long teamId) {
        return rateLimiter.tryAcquire("reco:team:" + teamId, 1, properties.rateLimit())
                && rateLimiter.tryAcquire("reco:team:daily:" + teamId, properties.dailyLimit(), DAILY_WINDOW);
    }
}
