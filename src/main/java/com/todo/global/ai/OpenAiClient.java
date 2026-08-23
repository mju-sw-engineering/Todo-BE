package com.todo.global.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 호출만 담당하는 인프라 부품. 프롬프트 문구와 결과 해석은 알지 못한다.
 *
 * <p><b>Chat Completions는 쓸 수 없다.</b> gpt-5.6 계열은 {@code /v1/chat/completions}에서
 * reasoning effort를 함께 지정하면 400으로 거절한다. effort는 비용 때문에 반드시 지정해야
 * 하므로 {@code /v1/responses}가 유일한 선택지다.
 */
@Slf4j
@Component
public class OpenAiClient {

    /** 요청/응답을 OpenAI 쪽에 보관하지 않는다. 인증 사진과 문서 본문이 오가기 때문이다. */
    private static final boolean STORE_ON_OPENAI = false;
    private static final String REASONING_MODE = "standard";
    private static final String CLIENT_REQUEST_ID_HEADER = "X-Client-Request-Id";

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * @param clientRequestId 호출 추적용 식별자. 폴러가 재시도하는 구조라 어느 호출이 어느
     *                        건이었는지 남아야 하고, 타임아웃으로 응답 헤더를 못 받은 경우에도
     *                        이 값으로 OpenAI 쪽 수신 여부를 조회할 수 있다. null이면 생략한다
     * @throws AiClientException 호출 실패. {@link AiClientException#isRetryable()}로 재시도 여부를 판단한다
     */
    public <T> T generateStructured(AiStructuredRequest request, Class<T> responseType, String clientRequestId) {
        String rawJson = callResponses(buildRequestBody(request), clientRequestId);
        try {
            return objectMapper.readValue(rawJson, responseType);
        } catch (JsonProcessingException e) {
            // strict 스키마를 걸었는데도 매핑이 안 되면 스키마와 DTO가 어긋난 것이다.
            // 같은 요청을 다시 보내도 결과가 같으므로 재시도하지 않는다.
            throw AiClientException.permanent("모델 응답을 예상한 형식으로 변환하지 못했습니다.", e);
        }
    }

    private Map<String, Object> buildRequestBody(AiStructuredRequest request) {
        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(Map.of("type", "input_text", "text", request.userText()));
        if (request.hasImage()) {
            // detail=low면 주제 부합 판정에는 충분하면서 이미지 토큰이 크게 준다.
            userContent.add(Map.of(
                    "type", "input_image",
                    "image_url", request.imageDataUrl(),
                    "detail", "low"
            ));
        }

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", request.schemaName());
        format.put("strict", true);
        format.put("schema", request.jsonSchema());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("store", STORE_ON_OPENAI);
        body.put("reasoning", Map.of("effort", properties.reasoningEffort(), "mode", REASONING_MODE));
        body.put("max_output_tokens", request.maxOutputTokens() != null
                ? request.maxOutputTokens()
                : properties.maxOutputTokens());
        body.put("text", Map.of("verbosity", "low", "format", format));
        body.put("input", List.of(
                Map.of("role", "system", "content", List.of(
                        Map.of("type", "input_text", "text", request.systemInstruction()))),
                Map.of("role", "user", "content", userContent)
        ));
        return body;
    }

    private String callResponses(Map<String, Object> body, String clientRequestId) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            // 재시도해도 키가 생기지 않는다. 사람이 설정을 고쳐야 한다.
            throw AiClientException.permanent("OpenAI API 키가 설정되지 않았습니다. OPENAI_API_KEY를 확인하세요.", null);
        }

        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .uri(properties.responsesUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .headers(headers -> {
                        if (clientRequestId != null && !clientRequestId.isBlank()) {
                            headers.set(CLIENT_REQUEST_ID_HEADER, clientRequestId);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw toStatusException(e, clientRequestId);
        } catch (ResourceAccessException e) {
            // 연결 실패·타임아웃. 응답 자체가 없으므로 본문에 남길 게 없다.
            throw AiClientException.retryable("OpenAI 호출이 타임아웃되었거나 연결에 실패했습니다.", e);
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            throw AiClientException.retryable("OpenAI 응답이 비어 있습니다.", null);
        }
        return extractOutputText(rawResponse, clientRequestId);
    }

    private AiClientException toStatusException(RestClientResponseException e, String clientRequestId) {
        HttpStatusCode status = e.getStatusCode();
        // 응답 본문에 키 값이 실릴 일은 없지만, 프롬프트 일부가 에코될 수 있어 본문 전체를
        // 남기지 않고 상태 코드와 추적 id만 남긴다.
        log.warn("OpenAI 호출 실패. status={}, clientRequestId={}", status.value(), clientRequestId);

        if (status.value() == 429 || status.value() == 408 || status.is5xxServerError()) {
            return AiClientException.retryable("OpenAI가 일시적으로 응답하지 못했습니다. status=" + status.value(), e);
        }
        // 401/403은 키 문제이고 400/422는 요청 자체가 잘못된 것이다. 둘 다 사람이 고쳐야 한다.
        return AiClientException.permanent("OpenAI 호출이 거절되었습니다. status=" + status.value(), e);
    }

    /**
     * Responses API는 결과를 {@code output} 배열로 준다. reasoning 항목이 앞에 오고 그 뒤에
     * assistant 메시지가 오므로, 배열 첫 번째가 아니라 타입으로 찾아야 한다.
     */
    private String extractOutputText(String rawResponse, String clientRequestId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw AiClientException.retryable("OpenAI 응답을 JSON으로 읽지 못했습니다.", e);
        }

        logUsage(root, clientRequestId);

        String status = root.path("status").asText("");
        if ("incomplete".equals(status)) {
            String reason = root.path("incomplete_details").path("reason").asText("unknown");
            // max_output_tokens로 잘렸다면 JSON도 잘려 있다. 같은 상한으로 재시도하면 또 잘린다.
            throw AiClientException.permanent("모델 응답이 완결되지 않았습니다. reason=" + reason, null);
        }

        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                String contentType = content.path("type").asText();
                if ("refusal".equals(contentType)) {
                    throw AiClientException.permanent("모델이 응답을 거부했습니다.", null);
                }
                if ("output_text".equals(contentType)) {
                    return content.path("text").asText();
                }
            }
        }
        throw AiClientException.permanent("모델 응답에서 결과 텍스트를 찾지 못했습니다.", null);
    }

    private void logUsage(JsonNode root, String clientRequestId) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode()) {
            return;
        }
        // 비용은 reasoning 토큰이 좌우한다. 출력 단가가 입력의 6배라, effort 설정이 잘못
        // 배포됐을 때 청구서보다 먼저 알아차릴 수 있는 곳이 이 로그다.
        log.info("OpenAI 사용량. model={}, input={}, output={}, reasoning={}, clientRequestId={}",
                properties.model(),
                usage.path("input_tokens").asInt(),
                usage.path("output_tokens").asInt(),
                usage.path("output_tokens_details").path("reasoning_tokens").asInt(),
                clientRequestId);
    }
}
