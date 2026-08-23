package com.todo.domain.todo.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationPromptProviderTest {

    private final RecommendationPromptProvider provider = new RecommendationPromptProvider();

    @BeforeEach
    void setUp() {
        provider.loadPrompts();
    }

    @Test
    void FULL과_STARTER_프롬프트를_리소스에서_읽는다() {
        String full = provider.systemInstruction(RecommendationMode.FULL);
        String starter = provider.systemInstruction(RecommendationMode.STARTER);

        assertThat(full).contains("<team_data>").contains("SPLIT").contains("REBALANCE").contains("자기 이름을 말하지 않는다");
        assertThat(starter).contains("<team_data>").contains("NEW만").contains("기록이 쌓이면 더 정확해져요");
        assertThat(full).isNotEqualTo(starter);
    }

    @Test
    void NONE에는_프롬프트가_없다() {
        assertThatThrownBy(() -> provider.systemInstruction(RecommendationMode.NONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 사용자_텍스트는_날짜와_팀원_수를_앞세우고_요약을_뒤에_붙인다() {
        TeamActivityDigest digest = new TeamActivityDigest(
                RecommendationMode.FULL, "<team_data>\n[팀] x\n</team_data>", LocalDate.of(2026, 8, 23), 1,
                Map.of(1L, "민수"), Set.of(), List.of());

        String text = provider.userText(digest);

        assertThat(text)
                .startsWith("오늘은 2026-08-23이고 팀원은 1명이다.")
                .contains("REBALANCE는 제안하지 않는다")
                .endsWith("<team_data>\n[팀] x\n</team_data>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 스키마는_strict_요건을_만족하고_observations가_맨_앞이다() {
        Map<String, Object> schema = provider.schema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat(properties.keySet()).containsExactly("observations", "greeting", "recommendations");
        assertThat((List<String>) schema.get("required")).containsExactlyElementsOf(properties.keySet());

        Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>) properties.get("recommendations")).get("items");
        Map<String, Object> itemProps = (Map<String, Object>) items.get("properties");
        assertThat(items.get("additionalProperties")).isEqualTo(false);
        assertThat((List<String>) items.get("required")).containsExactlyElementsOf(itemProps.keySet());
        assertThat(itemProps.keySet()).containsExactly(
                "kind", "title", "description", "reason", "suggested_deadline", "related_todo_id", "suggested_assignee_ids");
        assertThat((List<String>) ((Map<String, Object>) itemProps.get("kind")).get("enum"))
                .containsExactly("SPLIT", "RETRY", "NEW", "REBALANCE");
        assertThat(((Map<String, Object>) itemProps.get("related_todo_id")).get("type"))
                .isEqualTo(List.of("integer", "null"));
    }
}
