package com.todo.global.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @Mock
    private WebSocketSession session;
    @Mock
    private WebSocketSession otherSession;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    @Test
    void 바인딩된_사용자의_세션을_모두_강제_종료한다() throws IOException {
        given(session.getId()).willReturn("s1");
        registry.register(session);
        registry.bindUser("s1", 1L);

        registry.closeAllForUser(1L);

        then(session).should().close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void 다른_사용자의_세션은_닫지_않는다() throws IOException {
        given(session.getId()).willReturn("s1");
        given(otherSession.getId()).willReturn("s2");
        registry.register(session);
        registry.register(otherSession);
        registry.bindUser("s1", 1L);
        registry.bindUser("s2", 2L);

        registry.closeAllForUser(1L);

        then(session).should().close(CloseStatus.POLICY_VIOLATION);
        then(otherSession).should(never()).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void 등록_해제된_세션은_닫지_않는다() throws IOException {
        given(session.getId()).willReturn("s1");
        registry.register(session);
        registry.bindUser("s1", 1L);
        registry.unregister("s1");

        registry.closeAllForUser(1L);

        then(session).should(never()).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void 등록되지_않은_세션의_바인딩은_무시한다() {
        registry.bindUser("unknown", 1L);

        assertThatCode(() -> registry.closeAllForUser(1L)).doesNotThrowAnyException();
    }

    @Test
    void 세션ID가_없으면_바인딩하지_않는다() {
        assertThatCode(() -> registry.bindUser(null, 1L)).doesNotThrowAnyException();
    }

    @Test
    void 한_세션의_종료_실패가_다른_세션의_종료를_막지_않는다() throws IOException {
        given(session.getId()).willReturn("s1");
        given(otherSession.getId()).willReturn("s2");
        registry.register(session);
        registry.register(otherSession);
        registry.bindUser("s1", 1L);
        registry.bindUser("s2", 1L);
        willThrow(new IOException("close 실패")).given(session).close(CloseStatus.POLICY_VIOLATION);

        assertThatCode(() -> registry.closeAllForUser(1L)).doesNotThrowAnyException();

        then(otherSession).should().close(CloseStatus.POLICY_VIOLATION);
    }
}
