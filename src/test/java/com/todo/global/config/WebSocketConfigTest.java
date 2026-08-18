package com.todo.global.config;

import com.todo.global.websocket.WebSocketAuthChannelInterceptor;
import com.todo.global.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
    @Mock
    private WebSocketSessionRegistry webSocketSessionRegistry;
    @Mock
    private StompEndpointRegistry registry;
    @Mock
    private StompWebSocketEndpointRegistration registration;

    @Test
    void 웹소켓도_HTTP과_같은_origin만_허용한다() {
        CorsProperties corsProperties = new CorsProperties(List.of(
                "http://localhost:3000",
                "http://localhost:3001",
                "https://todo.bluerack.org"
        ));
        WebSocketConfig webSocketConfig = new WebSocketConfig(
                webSocketAuthChannelInterceptor,
                webSocketSessionRegistry,
                corsProperties
        );
        given(registry.addEndpoint("/ws")).willReturn(registration);
        given(registration.setAllowedOrigins(
                "http://localhost:3000",
                "http://localhost:3001",
                "https://todo.bluerack.org"
        )).willReturn(registration);

        webSocketConfig.registerStompEndpoints(registry);

        then(registration).should().setAllowedOrigins(
                "http://localhost:3000",
                "http://localhost:3001",
                "https://todo.bluerack.org"
        );
        then(registration).should().withSockJS();
    }
}
