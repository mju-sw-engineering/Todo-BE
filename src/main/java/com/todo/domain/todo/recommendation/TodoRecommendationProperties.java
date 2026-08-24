package com.todo.domain.todo.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 추천 기능의 스위치와 남용 방어 값.
 *
 * <p>AI 호출은 건당 비용이 나가고 결과가 팀 전체에 보이므로, 개인 단위가 아니라 <b>팀 단위</b>로
 * 막는다. 한 사람이 아껴 써도 팀원 넷이 번갈아 치면 같은 비용이 나간다.
 *
 * @param enabled      OpenAI 장애가 길어질 때 내리는 스위치. 명령어는 UNAVAILABLE 결과를 돌려준다
 * @param cooldown     직전 결과를 재사용하는 기간. 같은 데이터에 모델을 두 번 부르지 않는다
 * @param rateLimit    이 기간에 한 번만 호출을 허용한다
 * @param dailyLimit   팀당 하루 호출 상한
 */
@ConfigurationProperties(prefix = "todo-recommendation")
public record TodoRecommendationProperties(
        Boolean enabled,
        Duration cooldown,
        Duration rateLimit,
        Integer dailyLimit
) {
    public TodoRecommendationProperties {
        enabled = enabled == null || enabled;
        cooldown = cooldown == null ? Duration.ofMinutes(10) : cooldown;
        rateLimit = rateLimit == null ? Duration.ofMinutes(5) : rateLimit;
        dailyLimit = dailyLimit == null ? 10 : dailyLimit;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
