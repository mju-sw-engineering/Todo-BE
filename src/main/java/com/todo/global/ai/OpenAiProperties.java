package com.todo.global.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * OpenAI 호출 설정.
 *
 * <p>{@code reasoningEffort}가 비용의 지배 변수다. 기본값 medium으로 두면 판정·요약처럼
 * 추론이 거의 필요 없는 작업에도 reasoning 토큰이 붙는데, 이는 입력 단가의 6배인 출력
 * 단가로 계산돼 본문 입력비를 역전한다. 그래서 low를 기본으로 둔다.
 */
@Validated
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        /*
         * 일부러 @NotBlank를 붙이지 않는다. 키를 필수로 두면 OpenAI 키가 없는 팀원이
         * 앱 자체를 띄우지 못한다. AI 판정은 부가 기능이므로, 키가 없으면 호출 시점에
         * 영구 실패로 처리하고 나머지 기능은 그대로 동작하게 둔다.
         */
        String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String model,
        @NotBlank String reasoningEffort,
        @Positive int maxOutputTokens,
        Duration connectTimeout,
        Duration readTimeout
) {
    public OpenAiProperties {
        baseUrl = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
        reasoningEffort = reasoningEffort == null ? null : reasoningEffort.strip();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    public String responsesUrl() {
        return baseUrl + "/responses";
    }
}
