package com.todo.global.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

@Component
@ConditionalOnProperty(prefix = "websocket.stats.logging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class WebSocketStatsLogger {

    private final WebSocketMessageBrokerStats messageBrokerStats;

    @Scheduled(
            fixedDelayString = "${websocket.stats.logging.interval-ms:600000}",
            initialDelayString = "${websocket.stats.logging.initial-delay-ms:60000}"
    )
    public void logStats() {
        SubProtocolWebSocketHandler.Stats sessionStats = messageBrokerStats.getWebSocketSessionStats();
        StompSubProtocolHandler.Stats stompStats = messageBrokerStats.getStompSubProtocolStats();

        if (sessionStats == null || stompStats == null) {
            log.warn("WebSocket 통계를 조회할 수 없습니다.");
            return;
        }

        int currentSessions = sessionStats.getWebSocketSessions()
                + sessionStats.getHttpStreamingSessions()
                + sessionStats.getHttpPollingSessions();
        int abnormalClosures = sessionStats.getNoMessagesReceivedSessions()
                + sessionStats.getLimitExceededSessions()
                + sessionStats.getTransportErrorSessions();

        log.info(
                "WebSocket 통계 - 현재 세션={}, 누적 세션={}, STOMP 연결 성공/시도={}/{}, 비정상 종료={}",
                currentSessions,
                sessionStats.getTotalSessions(),
                stompStats.getTotalConnected(),
                stompStats.getTotalConnect(),
                abnormalClosures
        );
    }
}
