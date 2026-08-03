package com.todo.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리프레시 토큰 쿠키의 Secure 기본값이 안전한 쪽으로 유지되는지 고정한다.
 * 이 값이 false로 새면 2주짜리 리프레시 토큰이 평문 연결로 전송된다.
 * COOKIE_SECURE를 지정하지 않은 환경에서는 항상 true여야 하며,
 * 로컬 HTTP 테스트를 위해 COOKIE_SECURE=false를 셸에 걸어둔 경우에는 이 테스트가 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CookieSecurePropertyTest {

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @Test
    void 설정을_지정하지_않으면_Secure_기본값은_true다() {
        assertThat(cookieSecure).isTrue();
    }
}
