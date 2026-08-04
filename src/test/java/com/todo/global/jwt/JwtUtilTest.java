package com.todo.global.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-secret-key-for-jwt-signing-that-is-long-enough",
            60000,
            1209600000
    );

    @Test
    void 토큰을_생성하고_userId를_추출한다() {
        String token = jwtUtil.generateToken(1L);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(1L);
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void 잘못된_토큰은_유효하지_않다() {
        assertThat(jwtUtil.isValid("invalid-token")).isFalse();
    }

    @Test
    void setup_token을_생성하고_파싱한다() {
        String token = jwtUtil.generateSetupToken("apple-social-id", "auth-code-xyz", "com.test.app");

        assertThat(token).isNotBlank();
        Claims claims = jwtUtil.parseSetupToken(token);
        assertThat(claims.getSubject()).isEqualTo("apple-social-id");
        assertThat(claims.get("authCode", String.class)).isEqualTo("auth-code-xyz");
        assertThat(claims.get("clientId", String.class)).isEqualTo("com.test.app");
    }

    @Test
    void setup_token이_아닌_일반_토큰으로_파싱하면_예외를_던진다() {
        String token = jwtUtil.generateToken(1L);

        assertThatThrownBy(() -> jwtUtil.parseSetupToken(token))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
