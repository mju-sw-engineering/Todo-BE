package com.todo.domain.todo.recommendation;

import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationItem;
import com.todo.domain.todo.recommendation.holiday.Holiday;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationResultSanitizerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21); // 금요일
    private final RecommendationResultSanitizer sanitizer = new RecommendationResultSanitizer();

    private final TeamActivityDigest digest = new TeamActivityDigest(
            RecommendationMode.FULL, "<team_data>…</team_data>", TODAY, 2,
            Map.of(1L, "민수", 2L, "유나"),
            Set.of(10L, 11L),
            List.of(new Holiday(LocalDate.of(2026, 8, 24), "임시공휴일")));

    @Test
    void 정상_항목은_그대로_카드가_되고_index가_매겨진다() {
        AiRecommendationResponse response = response(
                item("SPLIT", "발표자료 — 개요 정리", "첫 단계만", "'발표자료'가 두 번 실패", "2026-08-25", 10L, List.of(1L)),
                item("NEW", "데모 시나리오 쓰기", "…", "…", "2026-08-26", null, List.of()));

        List<TeamTodoRecommendationItem> items = sanitizer.sanitize(response, digest);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).index()).isZero();
        assertThat(items.get(0).kind()).isEqualTo(RecommendationKind.SPLIT);
        assertThat(items.get(0).title()).isEqualTo("발표자료 — 개요 정리");
        assertThat(items.get(0).relatedTodoId()).isEqualTo(10L);
        assertThat(items.get(0).suggestedAssigneeIds()).containsExactly(1L);
        assertThat(items.get(0).suggestedDeadline())
                .isEqualTo(OffsetDateTime.of(2026, 8, 25, 21, 0, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(items.get(0).isRegistered()).isFalse();
        assertThat(items.get(1).index()).isEqualTo(1);
        assertThat(items.get(1).relatedTodoId()).isNull();
    }

    @Test
    void 세_개를_넘는_추천은_버린다() {
        AiRecommendationResponse response = response(
                item("NEW", "1", "", "", "2026-08-25", null, List.of()),
                item("NEW", "2", "", "", "2026-08-25", null, List.of()),
                item("NEW", "3", "", "", "2026-08-25", null, List.of()),
                item("NEW", "4", "", "", "2026-08-25", null, List.of()));

        assertThat(sanitizer.sanitize(response, digest)).hasSize(3);
    }

    @Test
    void 제목이_비어있는_항목은_버리고_index는_건너뛰지_않는다() {
        AiRecommendationResponse response = response(
                item("NEW", "  ", "", "", "2026-08-25", null, List.of()),
                item("NEW", "살아남은 것", "", "", "2026-08-25", null, List.of()));

        List<TeamTodoRecommendationItem> items = sanitizer.sanitize(response, digest);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).index()).isZero();
        assertThat(items.get(0).title()).isEqualTo("살아남은 것");
    }

    @Test
    void 과거_날짜나_읽을_수_없는_날짜는_다음_평일로_보정한다() {
        // 오늘 8/21(금) → 8/22 토, 8/23 일, 8/24 공휴일 → 8/25 화
        OffsetDateTime expected = OffsetDateTime.of(2026, 8, 25, 21, 0, 0, 0, ZoneOffset.ofHours(9));

        assertThat(sanitizer.resolveDeadline("2026-08-20", digest)).isEqualTo(expected);
        assertThat(sanitizer.resolveDeadline("다음주", digest)).isEqualTo(expected);
        assertThat(sanitizer.resolveDeadline(null, digest)).isEqualTo(expected);
    }

    @Test
    void 모델이_고른_공휴일이나_오늘은_손대지_않는다() {
        assertThat(sanitizer.resolveDeadline("2026-08-24", digest).toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(sanitizer.resolveDeadline("2026-08-21", digest).toLocalDate()).isEqualTo(TODAY);
    }

    @Test
    void 다른_팀_투두_id는_null로_비팀원_담당자는_제거한다() {
        AiRecommendationResponse response = response(
                item("RETRY", "다시", "", "", "2026-08-25", 999L, Arrays.asList(1L, 77L, 2L, 1L, null)));

        TeamTodoRecommendationItem item = sanitizer.sanitize(response, digest).get(0);

        assertThat(item.relatedTodoId()).isNull();
        assertThat(item.suggestedAssigneeIds()).containsExactly(1L, 2L);
    }

    @Test
    void 팀원이_한_명이면_REBALANCE는_NEW로_바꾼다() {
        TeamActivityDigest solo = new TeamActivityDigest(
                RecommendationMode.FULL, "", TODAY, 1, Map.of(1L, "민수"), Set.of(), List.of());
        AiRecommendationResponse response = response(
                item("REBALANCE", "나누기", "", "", "2026-08-25", null, List.of(1L)));

        assertThat(sanitizer.sanitize(response, solo).get(0).kind()).isEqualTo(RecommendationKind.NEW);
    }

    @Test
    void 알_수_없는_kind는_NEW로_떨어뜨린다() {
        AiRecommendationResponse response = response(
                item("SOMETHING_ELSE", "x", "", "", "2026-08-25", null, List.of()));

        assertThat(sanitizer.sanitize(response, digest).get(0).kind()).isEqualTo(RecommendationKind.NEW);
    }

    @Test
    void 긴_텍스트는_자르고_줄바꿈은_공백으로_누른다() {
        String longTitle = "가".repeat(200);
        AiRecommendationResponse response = response(
                item("NEW", longTitle, "설명\n둘째 줄", "근거\t탭", "2026-08-25", null, List.of()));

        TeamTodoRecommendationItem item = sanitizer.sanitize(response, digest).get(0);

        assertThat(item.title()).hasSize(RecommendationResultSanitizer.TITLE_MAX);
        assertThat(item.description()).isEqualTo("설명 둘째 줄");
        assertThat(item.reason()).isEqualTo("근거 탭");
    }

    @Test
    void 응답이_null이거나_추천_배열이_null이면_빈_목록이다() {
        assertThat(sanitizer.sanitize(null, digest)).isEmpty();
        assertThat(sanitizer.sanitize(new AiRecommendationResponse("", "", null), digest)).isEmpty();
    }

    private static AiRecommendationResponse response(AiRecommendationResponse.Item... items) {
        return new AiRecommendationResponse("관찰", "인사", List.of(items));
    }

    private static AiRecommendationResponse.Item item(
            String kind, String title, String description, String reason,
            String deadline, Long relatedTodoId, List<Long> assignees) {
        return new AiRecommendationResponse.Item(kind, title, description, reason, deadline, relatedTodoId, assignees);
    }
}
