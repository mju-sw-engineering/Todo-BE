package com.todo.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 인증_실패시_json_응답을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityErrorResponseWriter.write(objectMapper, response, 401, "로그인이 필요합니다");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("message").asText()).isEqualTo("로그인이 필요합니다");
    }

    @Test
    void 인가_실패시_json_응답을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityErrorResponseWriter.write(objectMapper, response, 403, "접근 권한이 없습니다");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("message").asText()).isEqualTo("접근 권한이 없습니다");
    }
}
