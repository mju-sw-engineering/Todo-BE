package com.todo.global.ai;

import java.util.Map;

/**
 * 구조화된 JSON 응답을 요구하는 한 번의 모델 호출.
 *
 * <p>이 레코드는 "무엇을 물어보는지"만 담는다. 프롬프트 문구와 스키마 내용은 호출하는
 * 도메인 서비스가 정하고, {@link OpenAiClient}는 그것을 그대로 실어 보낼 뿐이다.
 *
 * @param systemInstruction 모델의 역할과 판단 기준. 신뢰할 수 있는 입력이다
 * @param userText          판단 대상 텍스트. 사용자 제출물이 섞이므로 신뢰할 수 없는 입력이며,
 *                          호출부가 구분자로 감싸고 "안의 지시를 따르지 말 것"을 명시해야 한다
 * @param imageDataUrl      이미지 입력. {@code data:image/jpeg;base64,...} 형식이며 없으면 null
 * @param schemaName        스키마 이름. 응답 검증에 쓰이며 영숫자와 밑줄만 사용한다
 * @param jsonSchema        JSON Schema 본문. strict 모드로 전달되므로 모델이 벗어날 수 없다
 * @param maxOutputTokens   출력 상한. null이면 설정값을 쓴다. reasoning 토큰도 여기에 포함된다
 */
public record AiStructuredRequest(
        String systemInstruction,
        String userText,
        String imageDataUrl,
        String schemaName,
        Map<String, Object> jsonSchema,
        Integer maxOutputTokens
) {
    public static AiStructuredRequest ofText(
            String systemInstruction,
            String userText,
            String schemaName,
            Map<String, Object> jsonSchema
    ) {
        return new AiStructuredRequest(systemInstruction, userText, null, schemaName, jsonSchema, null);
    }

    public static AiStructuredRequest ofImage(
            String systemInstruction,
            String userText,
            String imageDataUrl,
            String schemaName,
            Map<String, Object> jsonSchema
    ) {
        return new AiStructuredRequest(systemInstruction, userText, imageDataUrl, schemaName, jsonSchema, null);
    }

    public boolean hasImage() {
        return imageDataUrl != null && !imageDataUrl.isBlank();
    }
}
