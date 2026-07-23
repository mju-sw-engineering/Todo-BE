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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final TodoSubscriptionValidator subscriptionValidator;

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

            String loginId = jwtUtil.extractLoginId(token);
            UserDetails userDetails = authService.loadUserByUsername(loginId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            accessor.setUser(authentication);

        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            java.security.Principal user = accessor.getUser();
            if (user == null) {
                throw new MessageDeliveryException("인증이 필요합니다.");
            }
            subscriptionValidator.validate(accessor.getDestination(), user.getName());
        }

        return message;
    }
}
