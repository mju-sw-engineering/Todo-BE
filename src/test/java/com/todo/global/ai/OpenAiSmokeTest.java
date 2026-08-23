package com.todo.global.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 OpenAI API를 호출해 요청 형태가 유효한지 확인한다. mock 테스트는 우리가 상상한
 * 형태만 검증하므로, API가 실제로 바뀌었는지는 이 테스트로만 알 수 있다. 모델을 교체하거나
 * 요청 형태를 손볼 때 한 번 돌린다.
 *
 * <p><b>실행에 비용이 발생하므로 기본적으로 건너뛴다.</b> API 키가 아니라 별도의 opt-in
 * 변수로 막는 이유는, 키가 개발·배포 환경에 상시로 깔리면 평범한 {@code ./gradlew test}가
 * 매번 OpenAI를 호출하게 되기 때문이다.
 *
 * <pre>
 * OPENAI_SMOKE_TEST=true OPENAI_API_KEY='sk-...' \
 *   ./gradlew test --tests 'com.todo.global.ai.OpenAiSmokeTest'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_SMOKE_TEST", matches = "(?i)true")
class OpenAiSmokeTest {

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "verdict", Map.of("type", "string", "enum", List.of("VERIFIED", "MISMATCH", "UNCERTAIN")),
                    "summary", Map.of("type", "string")
            ),
            "required", List.of("verdict", "summary"),
            "additionalProperties", false
    );

    /** 1x1 빨간 점 PNG. 이미지 입력 배관이 통하는지만 본다. */
    private static final String RED_DOT_PNG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42m"
                    + "P8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    record Verdict(String verdict, String summary) {}

    private OpenAiClient client() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assertThat(apiKey)
                .withFailMessage("OPENAI_SMOKE_TEST를 켰다면 OPENAI_API_KEY도 함께 설정해야 합니다.")
                .isNotBlank();

        OpenAiProperties properties = new OpenAiProperties(
                apiKey,
                "https://api.openai.com/v1",
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5.6-luna"),
                "low",
                400,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60)
        );
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        return new OpenAiClient(restClient, properties, new ObjectMapper());
    }

    @Test
    void 텍스트_구조화_응답이_실제로_온다() {
        Verdict result = client().generateStructured(
                AiStructuredRequest.ofText(
                        "너는 인증 검토자다. 할 일과 제출 내용이 맞는지 판정하고 한국어 한 문장으로 요약한다. "
                                + "확신이 없으면 UNCERTAIN을 택한다.",
                        "할 일: 점심 먹기\n제출 내용: 오늘 점심으로 짜장면을 먹었습니다.",
                        "proof_verdict",
                        SCHEMA),
                Verdict.class,
                "smoke-text");

        System.out.println("[SMOKE:text] verdict=" + result.verdict() + " summary=" + result.summary());
        assertThat(result.verdict()).isIn("VERIFIED", "MISMATCH", "UNCERTAIN");
        assertThat(result.summary()).isNotBlank();
    }

    @Test
    void 이미지_입력이_실제로_통한다() {
        Verdict result = client().generateStructured(
                AiStructuredRequest.ofImage(
                        "너는 인증 검토자다. 첨부된 사진이 할 일과 맞는지 판정하고 한국어 한 문장으로 요약한다. "
                                + "확신이 없으면 UNCERTAIN을 택한다.",
                        "할 일: 점심 먹기",
                        RED_DOT_PNG,
                        "proof_verdict",
                        SCHEMA),
                Verdict.class,
                "smoke-image");

        System.out.println("[SMOKE:image] verdict=" + result.verdict() + " summary=" + result.summary());
        assertThat(result.verdict()).isIn("VERIFIED", "MISMATCH", "UNCERTAIN");
    }
}
