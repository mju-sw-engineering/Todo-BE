package com.todo.global.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiClientTest {

    private static final String RESPONSES_URL = "https://api.openai.test/v1/responses";
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("verdict", Map.of("type", "string")),
            "required", java.util.List.of("verdict"),
            "additionalProperties", false
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private OpenAiClient client;

    record Verdict(String verdict) {}

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiClient(builder.build(), properties("test-key"), objectMapper);
    }

    private OpenAiProperties properties(String apiKey) {
        return new OpenAiProperties(
                apiKey,
                "https://api.openai.test/v1/",
                "gpt-5.6-luna",
                "low",
                400,
                Duration.ofSeconds(3),
                Duration.ofSeconds(30)
        );
    }

    /** 요청 본문을 꺼내 검증하기 위한 헬퍼. RequestMatcher는 예외만 던질 수 있어 캡처로 대신한다. */
    private RequestMatcher captureBody(AtomicReference<String> target) {
        return request -> target.set(new String(((MockClientHttpRequest) request).getBodyAsBytes()));
    }

    private String successBody(String outputJson) {
        return """
                {
                  "status": "completed",
                  "output": [
                    {"type": "reasoning", "summary": []},
                    {"type": "message", "role": "assistant",
                     "content": [{"type": "output_text", "text": %s}]}
                  ],
                  "usage": {"input_tokens": 1200, "output_tokens": 150,
                            "output_tokens_details": {"reasoning_tokens": 64}}
                }
                """.formatted(objectMapperWriteString(outputJson));
    }

    private String objectMapperWriteString(String raw) {
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 구조화_응답을_객체로_변환한다() {
        server.expect(requestTo(RESPONSES_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("X-Client-Request-Id", "analysis-7"))
                .andRespond(withSuccess(successBody("{\"verdict\":\"VERIFIED\"}"), MediaType.APPLICATION_JSON));

        Verdict result = client.generateStructured(
                AiStructuredRequest.ofText("지침", "할 일: 점심 먹기", "proof_verdict", SCHEMA),
                Verdict.class,
                "analysis-7"
        );

        assertThat(result.verdict()).isEqualTo("VERIFIED");
        server.verify();
    }

    @Test
    void 요청_본문에_모델과_비용_관련_설정을_담는다() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(captureBody(captured))
                .andRespond(withSuccess(successBody("{\"verdict\":\"VERIFIED\"}"), MediaType.APPLICATION_JSON));

        client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA),
                Verdict.class,
                null
        );

        JsonNode body = objectMapper.readTree(captured.get());
        assertThat(body.path("model").asText()).isEqualTo("gpt-5.6-luna");
        // 인증 사진·문서 본문이 OpenAI 쪽에 보관되면 안 된다.
        assertThat(body.path("store").asBoolean(true)).isFalse();
        assertThat(body.path("reasoning").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("reasoning").path("mode").asText()).isEqualTo("standard");
        assertThat(body.path("max_output_tokens").asInt()).isEqualTo(400);
        assertThat(body.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(body.path("text").path("format").path("name").asText()).isEqualTo("proof_verdict");
    }

    @Test
    void 이미지는_input_image로_담고_detail을_low로_보낸다() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        server.expect(requestTo(RESPONSES_URL))
                .andExpect(captureBody(captured))
                .andRespond(withSuccess(successBody("{\"verdict\":\"VERIFIED\"}"), MediaType.APPLICATION_JSON));

        client.generateStructured(
                AiStructuredRequest.ofImage("지침", "할 일: 점심", "data:image/jpeg;base64,AAAA", "proof_verdict", SCHEMA),
                Verdict.class,
                null
        );

        JsonNode userContent = objectMapper.readTree(captured.get()).path("input").get(1).path("content");
        assertThat(userContent).hasSize(2);
        assertThat(userContent.get(0).path("type").asText()).isEqualTo("input_text");
        assertThat(userContent.get(1).path("type").asText()).isEqualTo("input_image");
        assertThat(userContent.get(1).path("image_url").asText()).isEqualTo("data:image/jpeg;base64,AAAA");
        assertThat(userContent.get(1).path("detail").asText()).isEqualTo("low");
    }

    @Test
    void 이미지가_없으면_텍스트만_담는다() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        server.expect(requestTo(RESPONSES_URL))
                .andExpect(headerDoesNotExist("X-Client-Request-Id"))
                .andExpect(captureBody(captured))
                .andRespond(withSuccess(successBody("{\"verdict\":\"VERIFIED\"}"), MediaType.APPLICATION_JSON));

        client.generateStructured(
                AiStructuredRequest.ofText("지침", "문서 본문", "proof_verdict", SCHEMA),
                Verdict.class,
                null
        );

        JsonNode userContent = objectMapper.readTree(captured.get()).path("input").get(1).path("content");
        assertThat(userContent).hasSize(1);
        assertThat(userContent.get(0).path("type").asText()).isEqualTo("input_text");
    }

    @Test
    void reasoning_항목이_앞에_와도_message에서_결과를_찾는다() {
        server.expect(requestTo(RESPONSES_URL))
                .andRespond(withSuccess(successBody("{\"verdict\":\"UNCERTAIN\"}"), MediaType.APPLICATION_JSON));

        Verdict result = client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null);

        assertThat(result.verdict()).isEqualTo("UNCERTAIN");
    }

    @Test
    void 타임아웃은_재시도_가능한_실패로_분류한다() {
        server.expect(requestTo(RESPONSES_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, "analysis-1"))
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isTrue());
    }

    @Test
    void 요청_한도_초과와_서버_오류는_재시도_가능한_실패로_분류한다() {
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isTrue());

        server.reset();
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isTrue());
    }

    @Test
    void 인증_실패와_잘못된_요청은_재시도하지_않는다() {
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isFalse());

        server.reset();
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isFalse());
    }

    @Test
    void 응답이_max_output_tokens로_잘리면_재시도하지_않는다() {
        // 같은 상한으로 다시 보내면 또 잘린다. 사람이 상한을 올리거나 입력을 줄여야 한다.
        server.expect(requestTo(RESPONSES_URL)).andRespond(withSuccess("""
                {"status": "incomplete",
                 "incomplete_details": {"reason": "max_output_tokens"},
                 "output": []}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("max_output_tokens")
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isFalse());
    }

    @Test
    void 모델이_거부하면_재시도하지_않는다() {
        server.expect(requestTo(RESPONSES_URL)).andRespond(withSuccess("""
                {"status": "completed",
                 "output": [{"type": "message", "role": "assistant",
                             "content": [{"type": "refusal", "refusal": "거부"}]}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("거부")
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isFalse());
    }

    @Test
    void 결과_텍스트가_스키마와_어긋나면_재시도하지_않는다() {
        server.expect(requestTo(RESPONSES_URL))
                .andRespond(withSuccess(successBody("{\"unexpected\": 1}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isFalse());
    }

    @Test
    void 빈_응답은_재시도_가능한_실패로_분류한다() {
        server.expect(requestTo(RESPONSES_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isTrue());
    }

    @Test
    void API_키가_없으면_호출하지_않고_영구_실패로_처리한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer keylessServer = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient keyless = new OpenAiClient(builder.build(), properties("  "), objectMapper);

        assertThatThrownBy(() -> keyless.generateStructured(
                AiStructuredRequest.ofText("지침", "본문", "proof_verdict", SCHEMA), Verdict.class, null))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("OPENAI_API_KEY")
                .satisfies(e -> assertThat(((AiClientException) e).isRetryable()).isFalse());

        // 키가 없으면 네트워크로 나가지 않아야 한다.
        keylessServer.verify();
    }

    @Test
    void baseUrl_끝의_슬래시는_경로를_중복시키지_않는다() {
        assertThat(properties("k").responsesUrl()).isEqualTo("https://api.openai.test/v1/responses");
    }
}
