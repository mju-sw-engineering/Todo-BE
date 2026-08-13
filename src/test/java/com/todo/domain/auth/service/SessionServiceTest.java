package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.response.SessionResponse;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.event.NewDeviceLoginDetectedEvent;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = LocalDateTime.of(2026, 8, 11, 12, 0).atZone(KST).toInstant();

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SessionService sessionService;
    private User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, KST);
        sessionService = new SessionService(refreshTokenRepository, userRepository, clock, eventPublisher);
        user = User.create("1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private RefreshToken activeToken(String token, String deviceId, LocalDateTime createdAt) {
        RefreshToken rt = RefreshToken.create(user, token, deviceId, createdAt.plusDays(14));
        ReflectionTestUtils.setField(rt, "createdAt", createdAt);
        return rt;
    }

    @Test
    void 세션_발급은_동시_로그인_직렬화를_위해_사용자_행을_잠근다() {
        given(refreshTokenRepository.findActiveByUserId(anyLong(), any())).willReturn(List.of());

        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        sessionService.issueRefreshToken(user, "new-token", "new-device", now.plusDays(14));

        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    void 세션이_5개_이하면_정리하지_않는다() {
        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        List<RefreshToken> active = List.of(
                activeToken("t5", "d5", now),
                activeToken("t4", "d4", now.minusMinutes(1)),
                activeToken("t3", "d3", now.minusMinutes(2)),
                activeToken("t2", "d2", now.minusMinutes(3)),
                activeToken("t1", "d1", now.minusMinutes(4))
        );
        given(refreshTokenRepository.findActiveByUserId(anyLong(), any())).willReturn(active);

        sessionService.issueRefreshToken(user, "new-token", "new-device", now.plusDays(14));

        verify(refreshTokenRepository, never()).deleteAll(any());
    }

    @Test
    void 세션이_5개를_넘으면_가장_오래된_것부터_정리한다() {
        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        RefreshToken oldest = activeToken("t1", "d1", now.minusMinutes(5));
        List<RefreshToken> active = List.of(
                activeToken("t6", "d6", now),
                activeToken("t5", "d5", now.minusMinutes(1)),
                activeToken("t4", "d4", now.minusMinutes(2)),
                activeToken("t3", "d3", now.minusMinutes(3)),
                activeToken("t2", "d2", now.minusMinutes(4)),
                oldest
        );
        given(refreshTokenRepository.findActiveByUserId(anyLong(), any())).willReturn(active);

        sessionService.issueRefreshToken(user, "new-token", "new-device", now.plusDays(14));

        ArgumentCaptor<List<RefreshToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(refreshTokenRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(oldest);
    }

    @Test
    void 세션_목록은_현재_토큰과_일치하는_항목만_current로_표시한다() {
        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        RefreshToken current = activeToken("current-token", "d1", now);
        RefreshToken other = activeToken("other-token", "d2", now.minusMinutes(1));
        given(refreshTokenRepository.findActiveByUserId(1L, now)).willReturn(List.of(current, other));

        List<SessionResponse> sessions = sessionService.listSessions("1", "current-token");

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).current()).isTrue();
        assertThat(sessions.get(1).current()).isFalse();
    }

    @Test
    void 본인_세션을_삭제한다() {
        given(refreshTokenRepository.deleteByIdAndUserId(10L, 1L)).willReturn(1);

        sessionService.revokeSession("1", 10L);

        verify(refreshTokenRepository).deleteByIdAndUserId(10L, 1L);
    }

    @Test
    void 존재하지_않거나_남의_세션이면_404를_던진다() {
        given(refreshTokenRepository.deleteByIdAndUserId(10L, 1L)).willReturn(0);

        assertThatThrownBy(() -> sessionService.revokeSession("1", 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 전체_세션을_삭제한다() {
        sessionService.revokeAllSessions("1");

        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    void 기존_세션이_있고_새_기기이면_새기기_로그인_이벤트를_발행한다() {
        given(refreshTokenRepository.findActiveByUserId(anyLong(), any())).willReturn(List.of());
        given(refreshTokenRepository.existsByUser_Id(1L)).willReturn(true);
        given(refreshTokenRepository.existsByUser_IdAndDeviceId(1L, "new-device")).willReturn(false);

        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        sessionService.issueRefreshToken(user, "new-token", "new-device", now.plusDays(14));

        verify(eventPublisher).publishEvent(new NewDeviceLoginDetectedEvent(1L));
    }

    @Test
    void 이미_알고_있는_기기이면_이벤트를_발행하지_않는다() {
        given(refreshTokenRepository.findActiveByUserId(anyLong(), any())).willReturn(List.of());
        given(refreshTokenRepository.existsByUser_Id(1L)).willReturn(true);
        given(refreshTokenRepository.existsByUser_IdAndDeviceId(1L, "known-device")).willReturn(true);

        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        sessionService.issueRefreshToken(user, "new-token", "known-device", now.plusDays(14));

        verify(eventPublisher, never()).publishEvent(any(NewDeviceLoginDetectedEvent.class));
    }

    @Test
    void 이_계정의_첫_세션이면_이벤트를_발행하지_않는다() {
        given(refreshTokenRepository.findActiveByUserId(anyLong(), any())).willReturn(List.of());
        given(refreshTokenRepository.existsByUser_Id(1L)).willReturn(false);

        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        sessionService.issueRefreshToken(user, "new-token", "first-device", now.plusDays(14));

        verify(eventPublisher, never()).publishEvent(any(NewDeviceLoginDetectedEvent.class));
    }

    @Test
    void deviceId가_없으면_새_기기_여부를_판단하지_않고_이벤트를_발행하지_않는다() {
        given(refreshTokenRepository.findActiveByUserId(anyLong(), any())).willReturn(List.of());

        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, KST));
        sessionService.issueRefreshToken(user, "new-token", null, now.plusDays(14));

        verify(refreshTokenRepository, never()).existsByUser_IdAndDeviceId(anyLong(), anyString());
        verify(eventPublisher, never()).publishEvent(any(NewDeviceLoginDetectedEvent.class));
    }
}
