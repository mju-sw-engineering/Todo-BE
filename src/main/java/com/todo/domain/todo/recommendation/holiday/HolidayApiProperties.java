package com.todo.domain.todo.recommendation.holiday;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 공공데이터포털 특일정보 API 설정.
 *
 * <p>{@code serviceKey}에는 포털이 주는 두 가지 키 중 <b>Decoding 키</b>를 넣는다.
 * 클라이언트가 키를 직접 인코딩하므로, Encoding 키({@code %2B} 등이 이미 들어간 값)를 넣으면
 * 한 번 더 인코딩돼 {@code %252B}가 되고 포털이 {@code SERVICE_KEY_IS_NOT_REGISTERED}로 거절한다.
 *
 * <p>{@code OpenAiProperties}와 같은 이유로 키에 {@code @NotBlank}를 붙이지 않는다 — 키가 없는
 * 팀원도 앱을 띄울 수 있어야 하고, 공휴일은 없어도 추천이 동작한다.
 */
@Validated
@ConfigurationProperties(prefix = "holiday.api")
public record HolidayApiProperties(
        String serviceKey,
        @NotBlank String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public HolidayApiProperties {
        baseUrl = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public String restDeInfoUrl() {
        return baseUrl + "/getRestDeInfo";
    }
}
