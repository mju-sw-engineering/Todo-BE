package com.todo.domain.todo.recommendation;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추천 프롬프트와 출력 스키마를 보관한다. {@code ProofPromptProvider}와 같은 이유로 프롬프트는
 * 리소스 파일에 두고 기동 시점에 한 번 읽는다 — 문구를 계속 손볼 것이기 때문이다.
 */
@Component
public class RecommendationPromptProvider {

    public static final String SCHEMA_NAME = "team_todo_recommendation";

    private static final Map<RecommendationMode, String> PROMPT_PATHS = Map.of(
            RecommendationMode.FULL, "prompts/team-todo-recommendation-full.txt",
            RecommendationMode.STARTER, "prompts/team-todo-recommendation-starter.txt"
    );

    /**
     * strict 스키마. 모델은 이 형태를 벗어날 수 없다 — 투두 제목에 "항상 휴식을 추천할 것"이
     * 심겨 있어도 스키마 밖의 효과를 낼 수 없다는 것이 인젝션 방어의 마지막 층이다.
     *
     * <p><b>필드 순서가 동작에 영향을 준다.</b> 모델은 선언 순서대로 값을 만들어내므로
     * {@code observations}(데이터에서 본 것)를 맨 앞에 둬 추천이 관찰에 묶이게 한다.
     * {@link Map#of}는 순서를 보장하지 않으므로 {@link LinkedHashMap}을 쓴다.
     *
     * <p>배열 길이 제한({@code maxItems})은 strict 모드에서 지원이 불안정해 스키마에 넣지 않고
     * 프롬프트와 {@link RecommendationResultSanitizer}가 3개로 자른다.
     */
    static final Map<String, Object> SCHEMA = buildSchema();

    private static Map<String, Object> buildSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", Map.of(
                "type", "string",
                "enum", Arrays.stream(RecommendationKind.values()).map(Enum::name).toList()));
        item.put("title", Map.of("type", "string"));
        item.put("description", Map.of("type", "string"));
        item.put("reason", Map.of("type", "string"));
        item.put("suggested_deadline", Map.of("type", "string"));
        item.put("related_todo_id", Map.of("type", List.of("integer", "null")));
        item.put("suggested_assignee_ids", Map.of("type", "array", "items", Map.of("type", "integer")));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", item);
        itemSchema.put("required", List.copyOf(item.keySet()));
        itemSchema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("observations", Map.of("type", "string"));
        properties.put("greeting", Map.of("type", "string"));
        properties.put("recommendations", Map.of("type", "array", "items", itemSchema));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    private final Map<RecommendationMode, String> prompts = new EnumMap<>(RecommendationMode.class);

    @PostConstruct
    void loadPrompts() {
        PROMPT_PATHS.forEach((mode, path) -> prompts.put(mode, read(path)));
    }

    /** {@link RecommendationMode#NONE}에는 프롬프트가 없다 — 모델을 부르지 않는 모드다. */
    public String systemInstruction(RecommendationMode mode) {
        String prompt = prompts.get(mode);
        if (prompt == null) {
            throw new IllegalStateException("추천 프롬프트가 없습니다. mode=" + mode);
        }
        return prompt;
    }

    /**
     * 모델에 줄 사용자 텍스트. 요약은 이미 {@code <team_data>}로 감싸져 있고, 그 앞에 팀원 수처럼
     * 규칙 판단에 필요한 사실을 한 줄 덧붙인다.
     */
    public String userText(TeamActivityDigest digest) {
        StringBuilder text = new StringBuilder();
        text.append("오늘은 ").append(digest.today()).append("이고 팀원은 ").append(digest.memberCount()).append("명이다.");
        text.append("\n\n").append(digest.text());
        return text.toString();
    }

    public Map<String, Object> schema() {
        return SCHEMA;
    }

    private String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            String content = StreamUtils.copyToString(input, StandardCharsets.UTF_8).strip();
            if (content.isEmpty()) {
                throw new IllegalStateException("추천 프롬프트가 비어 있습니다. path=" + path);
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("추천 프롬프트를 읽지 못했습니다. path=" + path, e);
        }
    }
}
