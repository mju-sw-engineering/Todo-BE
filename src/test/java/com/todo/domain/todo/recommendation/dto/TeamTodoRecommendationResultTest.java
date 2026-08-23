package com.todo.domain.todo.recommendation.dto;

import com.todo.domain.todo.recommendation.RecommendationKind;
import com.todo.domain.todo.recommendation.RecommendationOutcome;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamTodoRecommendationResultTest {

    private static final TeamTodoRecommendationItem ITEM = TeamTodoRecommendationItem.of(
            0, RecommendationKind.NEW, "제목", "설명", "근거",
            OffsetDateTime.of(2026, 8, 25, 21, 0, 0, 0, ZoneOffset.ofHours(9)), null, List.of());

    @Test
    void 추천이_비면_READY와_STARTER_모두_EMPTY가_된다() {
        assertThat(TeamTodoRecommendationResult.ready("인사", List.of()).outcome()).isEqualTo(RecommendationOutcome.EMPTY);
        assertThat(TeamTodoRecommendationResult.starter("인사", List.of()).outcome()).isEqualTo(RecommendationOutcome.EMPTY);
        assertThat(TeamTodoRecommendationResult.ready("인사", List.of(ITEM)).outcome()).isEqualTo(RecommendationOutcome.READY);
        assertThat(TeamTodoRecommendationResult.starter("인사", List.of(ITEM)).outcome()).isEqualTo(RecommendationOutcome.STARTER);
    }

    @Test
    void EMPTY는_모델_인사가_비어있으면_고정_문구를_쓴다() {
        assertThat(TeamTodoRecommendationResult.empty("  ").greeting()).isEqualTo(TeamTodoRecommendationResult.EMPTY_GREETING);
        assertThat(TeamTodoRecommendationResult.empty("잘 하고 있어요").greeting()).isEqualTo("잘 하고 있어요");
    }

    @Test
    void 고정_결과들은_카드_없이_문구와_outcome만_가진다() {
        TeamTodoRecommendationResult cooldown = TeamTodoRecommendationResult.cooldown(1234L);

        assertThat(cooldown.outcome()).isEqualTo(RecommendationOutcome.COOLDOWN);
        assertThat(cooldown.previousMessageId()).isEqualTo(1234L);
        assertThat(cooldown.items()).isEmpty();
        assertThat(TeamTodoRecommendationResult.none().outcome()).isEqualTo(RecommendationOutcome.NONE);
        assertThat(TeamTodoRecommendationResult.unavailable().outcome()).isEqualTo(RecommendationOutcome.UNAVAILABLE);
    }

    @Test
    void 등록되면_해당_칸만_교체된다() {
        TeamTodoRecommendationResult result = TeamTodoRecommendationResult.ready("인사", List.of(ITEM, ITEM));

        TeamTodoRecommendationResult updated = result.withItem(0, ITEM.withRegistration(500L, 1L, "민수"));

        assertThat(updated.items().get(0).isRegistered()).isTrue();
        assertThat(updated.items().get(0).registeredTodoId()).isEqualTo(500L);
        assertThat(updated.items().get(0).registeredByNickname()).isEqualTo("민수");
        assertThat(updated.items().get(1).isRegistered()).isFalse();
        assertThat(result.items().get(0).isRegistered()).isFalse();
    }
}
