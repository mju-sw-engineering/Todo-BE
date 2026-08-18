package com.todo.global.websocket;

import com.todo.domain.auth.service.AuthService;
import com.todo.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final TeamSubscriptionValidator subscriptionValidator;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String bearerToken = accessor.getFirstNativeHeader("Authorization");

            if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Authorization 헤더가 없거나 형식이 올바르지 않습니다.");
            }

            String token = bearerToken.substring(7);

            if (!jwtUtil.isValid(token)) {
                throw new MessageDeliveryException("유효하지 않은 토큰입니다.");
            }

            Long userId;
            UserDetails userDetails;
            try {
                userId = jwtUtil.extractUserId(token);
                userDetails = authService.loadUserByUsername(userId.toString());
            } catch (UsernameNotFoundException | NumberFormatException e) {
                // subject가 loginId였던 구버전 토큰은 숫자 파싱에 실패한다.
                throw new MessageDeliveryException("유효하지 않은 토큰입니다.");
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            accessor.setUser(authentication);
            // 강퇴·탈퇴 시 이 사용자의 세션을 찾아 끊을 수 있도록 세션에 사용자를 연결한다
            sessionRegistry.bindUser(accessor.getSessionId(), userId);

        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            java.security.Principal user = accessor.getUser();
            if (user == null) {
                throw new MessageDeliveryException("인증이 필요합니다.");
            }
            subscriptionValidator.validate(accessor.getDestination(), user.getName());

        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                throw new MessageDeliveryException("인증이 필요합니다.");
            }
            // /topic·/queue로 직접 SEND하면 컨트롤러의 멤버십·페이로드 검증 없이
            // 브로커가 구독자에게 그대로 전달하므로, 애플리케이션 경로만 허용한다.
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/app/")) {
                throw new MessageDeliveryException("허용되지 않은 발행 경로입니다.");
            }
        }

        return message;
    }
}
