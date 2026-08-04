package com.todo.global.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
