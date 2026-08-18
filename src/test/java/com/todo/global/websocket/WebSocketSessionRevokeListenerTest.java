package com.todo.global.websocket;

import com.todo.domain.team.event.TeamMembershipRevokedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRevokeListenerTest {

    @InjectMocks
    private WebSocketSessionRevokeListener listener;

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Test
    void 멤버십_상실_이벤트를_받으면_해당_사용자의_세션을_모두_닫는다() {
        listener.onTeamMembershipRevoked(new TeamMembershipRevokedEvent(1L));

        then(sessionRegistry).should().closeAllForUser(1L);
    }
}
