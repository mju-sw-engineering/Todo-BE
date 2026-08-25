package com.todo.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:3001",
            "https://todo.bluerack.org"
    );

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000",
            "http://localhost:3001",
            "https://todo.bluerack.org"
    })
    void 등록된_origin은_credential_CORS_요청을_허용한다(String origin) {
        CorsConfiguration configuration = corsConfiguration();

        assertThat(configuration.checkOrigin(origin)).isEqualTo(origin);
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3002",
            "http://127.0.0.1:3000",
            "https://evil.bluerack.org",
            "https://example.com"
    })
    void 등록되지_않은_origin은_CORS_요청을_거부한다(String origin) {
        assertThat(corsConfiguration().checkOrigin(origin)).isNull();
    }

    @Test
    void preflight_응답을_1시간_캐시하도록_maxAge를_설정한다() {
        assertThat(corsConfiguration().getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void HTTP_메서드와_요청_헤더를_허용한다() {
        CorsConfiguration configuration = corsConfiguration();

        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("*");
    }

    private CorsConfiguration corsConfiguration() {
        CorsConfig corsConfig = new CorsConfig(new CorsProperties(ALLOWED_ORIGINS));
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) corsConfig.corsConfigurationSource();
        return source.getCorsConfigurations().get("/**");
    }
}
