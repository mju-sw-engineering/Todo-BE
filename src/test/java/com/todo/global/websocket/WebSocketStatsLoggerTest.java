package com.todo.global.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class WebSocketStatsLoggerTest {

    @Mock
    private WebSocketMessageBrokerStats messageBrokerStats;

    @Mock
    private SubProtocolWebSocketHandler.Stats sessionStats;

    @Mock
    private StompSubProtocolHandler.Stats stompStats;

    private WebSocketStatsLogger logger;

    @BeforeEach
    void setUp() {
        logger = new WebSocketStatsLogger(messageBrokerStats);
    }

    @Test
    void 웹소켓_통계를_한줄로_요약한다(CapturedOutput output) {
        given(messageBrokerStats.getWebSocketSessionStats()).willReturn(sessionStats);
        given(messageBrokerStats.getStompSubProtocolStats()).willReturn(stompStats);
        given(sessionStats.getWebSocketSessions()).willReturn(1);
        given(sessionStats.getHttpStreamingSessions()).willReturn(2);
        given(sessionStats.getHttpPollingSessions()).willReturn(3);
        given(sessionStats.getTotalSessions()).willReturn(24);
        given(sessionStats.getNoMessagesReceivedSessions()).willReturn(1);
        given(sessionStats.getLimitExceededSessions()).willReturn(2);
        given(sessionStats.getTransportErrorSessions()).willReturn(3);
        given(stompStats.getTotalConnected()).willReturn(21);
        given(stompStats.getTotalConnect()).willReturn(24);

        logger.logStats();

        assertThat(output).contains(
                "WebSocket 통계 - 현재 세션=6, 누적 세션=24, "
                        + "STOMP 연결 성공/시도=21/24, 비정상 종료=6"
        );
    }

    @Test
    void 통계가_준비되지_않으면_경고를_남긴다(CapturedOutput output) {
        given(messageBrokerStats.getWebSocketSessionStats()).willReturn(null);
        given(messageBrokerStats.getStompSubProtocolStats()).willReturn(stompStats);

        logger.logStats();

        assertThat(output).contains("WebSocket 통계를 조회할 수 없습니다.");
    }
}
