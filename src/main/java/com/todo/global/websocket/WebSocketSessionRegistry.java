package com.todo.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접속 중인 WebSocket 세션을 사용자 기준으로 추적한다.
 *
 * <p>세션 등록은 transport 레벨(handshake 직후)에서, 사용자 바인딩은 STOMP CONNECT
 * 프레임 인증 성공 시점에 일어난다 — 인증이 handshake가 아니라 CONNECT 프레임에서
 * 이뤄지므로 두 단계로 나뉜다. 강퇴·탈퇴로 멤버십이 사라져도 브로커 구독은 남기 때문에,
 * 그 사용자의 세션을 닫아 재연결 시 SUBSCRIBE 검증을 다시 거치게 만드는 것이 목적이다.
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, Long> userIdBySessionId = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessionsById.put(session.getId(), session);
    }

    public void unregister(String sessionId) {
        sessionsById.remove(sessionId);
        userIdBySessionId.remove(sessionId);
    }

    public void bindUser(String sessionId, Long userId) {
        // 세션 ID가 없거나 이미 종료돼 등록이 해제된 세션이면 바인딩을 남기지 않는다
        if (sessionId == null || !sessionsById.containsKey(sessionId)) {
            return;
        }
        userIdBySessionId.put(sessionId, userId);
    }

    public void closeAllForUser(Long userId) {
        userIdBySessionId.forEach((sessionId, boundUserId) -> {
            if (!boundUserId.equals(userId)) {
                return;
            }
            WebSocketSession session = sessionsById.get(sessionId);
            if (session == null) {
                return;
            }
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                // 한 세션 종료 실패가 나머지 세션 정리를 막지 않게 한다
                log.warn("WebSocket 세션 강제 종료 실패. sessionId={}, userId={}", sessionId, userId, e);
            }
        });
    }
}
