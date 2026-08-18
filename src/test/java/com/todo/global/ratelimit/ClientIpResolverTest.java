package com.todo.global.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void 헤더가_없으면_remoteAddr을_쓴다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(new ClientIpResolver(1).resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void 요청이_없으면_unknown을_반환한다() {
        assertThat(new ClientIpResolver(1).resolve(null)).isEqualTo("unknown");
    }

    @Test
    void 프록시가_한_단이면_마지막_값을_신뢰한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        // 앞쪽 값은 클라이언트가 위조해 보낼 수 있고, 마지막 값만 프록시가 관찰한 주소다
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.7");

        assertThat(new ClientIpResolver(1).resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 프록시가_두_단이면_오른쪽에서_두_번째_값을_신뢰한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.7, 198.51.100.2");

        assertThat(new ClientIpResolver(2).resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 헤더가_신뢰_홉_수보다_짧으면_가장_왼쪽_값을_쓴다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertThat(new ClientIpResolver(3).resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void 헤더가_공백이면_remoteAddr로_폴백한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(new ClientIpResolver(1).resolve(request)).isEqualTo("10.0.0.1");
    }
}
