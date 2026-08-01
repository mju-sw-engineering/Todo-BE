package com.todo.global.websocket;

import com.todo.domain.auth.service.AuthService;
import com.todo.global.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthService authService;
    @Mock
    private TeamSubscriptionValidator subscriptionValidator;
    @Mock
    private MessageChannel channel;

    @Test
    void connect_요청의_bearer_토큰이_유효하면_user를_설정한다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, "Bearer valid-token");
        given(jwtUtil.isValid("valid-token")).willReturn(true);
        given(jwtUtil.extractLoginId("valid-token")).willReturn("user1");
        UserDetails userDetails = new User("user1", "password", List.of());
        given(authService.loadUserByUsername("user1")).willReturn(userDetails);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        Principal user = accessor.getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("user1");
    }

    @Test
    void connect_요청에_authorization_헤더가_없으면_예외를_던진다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("Authorization 헤더가 없거나 형식이 올바르지 않습니다.");
    }

    @Test
    void connect_요청의_토큰이_유효하지_않으면_예외를_던진다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, "Bearer invalid-token");
        given(jwtUtil.isValid("invalid-token")).willReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
    }

    @Test
    void connect_요청의_사용자가_삭제됐으면_연결을_거부한다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, "Bearer deleted-user-token");
        given(jwtUtil.isValid("deleted-user-token")).willReturn(true);
        given(jwtUtil.extractLoginId("deleted-user-token")).willReturn("deleted-user");
        given(authService.loadUserByUsername("deleted-user"))
                .willThrow(new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
    }

    @Test
    void connect가_아닌_메시지는_그대로_반환한다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessage(StompCommand.SEND, null);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        then(jwtUtil).should(never()).isValid(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void subscribe_요청은_팀원_검증을_위임한다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessageWithUser(StompCommand.SUBSCRIBE, "/topic/teams/10", "user1");

        interceptor.preSend(message, channel);

        then(subscriptionValidator).should().validate("/topic/teams/10", "user1");
    }

    @Test
    void subscribe_요청의_팀원_검증_실패시_예외를_던진다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessageWithUser(StompCommand.SUBSCRIBE, "/topic/teams/10", "user1");
        willThrow(new MessageDeliveryException("해당 채널을 구독할 권한이 없습니다."))
                .given(subscriptionValidator).validate("/topic/teams/10", "user1");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("해당 채널을 구독할 권한이 없습니다.");
    }

    @Test
    void subscribe_요청에_인증_정보가_없으면_예외를_던진다() {
        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, authService, subscriptionValidator);
        Message<byte[]> message = stompMessage(StompCommand.SUBSCRIBE, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("인증이 필요합니다.");
    }

    private Message<byte[]> stompMessage(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> stompMessageWithUser(StompCommand command, String destination, String loginId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(() -> loginId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
