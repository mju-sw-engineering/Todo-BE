package com.todo.global.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 프록시 뒤에서 실제 클라이언트 IP를 고른다. IP 기준 rate limit 키에 쓴다.
 *
 * <p>X-Forwarded-For는 클라이언트가 임의로 채워 보낼 수 있고 프록시는 거기에 덧붙이기만 하므로
 * 맨 앞 값은 조작 가능하다. 신뢰할 수 있는 건 우리 프록시가 직접 관찰해 덧붙인 값뿐이라,
 * 오른쪽에서 {@code trustedProxyHops}번째를 고른다. 헤더가 그보다 짧으면 앞쪽이 잘린
 * 것이므로 남은 것 중 가장 왼쪽을 쓴다.
 */
@Component
public class ClientIpResolver {

    /**
     * 앱 앞단에 있는, 우리가 신뢰하는 프록시 단 수. 운영은 Coolify(Traefik) 한 단이라 1이다.
     * CDN 등을 앞에 추가하면 이 값을 함께 올려야 한다. 값이 실제 구성보다 작으면
     * 모든 사용자가 프록시 IP 하나로 묶여 IP 기준 한도를 공유하게 된다.
     */
    private final int trustedProxyHops;

    public ClientIpResolver(@Value("${app.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public String resolve(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return "unknown";
        }
        String forwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            int index = Math.max(0, hops.length - trustedProxyHops);
            String trustedHop = hops[index].trim();
            if (!trustedHop.isEmpty()) {
                return trustedHop;
            }
        }
        String remoteAddr = httpRequest.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }
}
