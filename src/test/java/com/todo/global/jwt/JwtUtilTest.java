package com.todo.global.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-secret-key-for-jwt-signing-that-is-long-enough",
            60000
    );

    @Test
    void 토큰을_생성하고_loginId를_추출한다() {
        String token = jwtUtil.generateToken("user1");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractLoginId(token)).isEqualTo("user1");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void 잘못된_토큰은_유효하지_않다() {
        assertThat(jwtUtil.isValid("invalid-token")).isFalse();
    }
}
