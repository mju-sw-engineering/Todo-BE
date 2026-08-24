package com.todo.domain.todo.recommendation;

import com.todo.domain.todo.recommendation.holiday.Holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 모델에 넘길 팀 활동 요약과, 모델 답을 검증할 때 필요한 사실들.
 *
 * @param mode            어떤 프롬프트를 쓸지 (또는 부르지 않을지)
 * @param text            모델 입력 본문. 사용자 입력(제목·설명)이 섞여 있으므로 신뢰할 수 없는 텍스트다
 * @param today           요약 기준일 (KST)
 * @param memberCount     팀원 수. 모델에 상황을 알려주는 맥락으로 쓴다
 * @param memberNicknames 팀원 id → 닉네임. 검증기가 비팀원 담당자를 걸러낼 때 쓴다
 * @param todoIds         요약에 등장한 투두 id. 검증기가 다른 팀 투두 참조를 걸러낼 때 쓴다
 * @param holidays        기준일부터 2주 내 공휴일. 검증기가 마감을 평일로 보정할 때 쓴다
 */
public record TeamActivityDigest(
        RecommendationMode mode,
        String text,
        LocalDate today,
        int memberCount,
        Map<Long, String> memberNicknames,
        Set<Long> todoIds,
        List<Holiday> holidays
) {
    public boolean isMember(Long userId) {
        return userId != null && memberNicknames.containsKey(userId);
    }

    public boolean hasTodo(Long todoId) {
        return todoId != null && todoIds.contains(todoId);
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.stream().anyMatch(h -> h.date().equals(date));
    }
}
