package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.response.SessionResponse;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리프레시 토큰(세션)의 발급·조회·삭제와 계정당 활성 세션 수 제한을 담당한다.
 *
 * <p>{@link AuthService}와 {@link AppleAuthService}가 로그인 방식은 다르지만 세션 발급
 * 이후 동작(5개 초과분 정리 등)은 동일해서 여기로 모은다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    static final int MAX_ACTIVE_SESSIONS = 5;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 새 세션을 저장하고, 계정당 활성 세션이 {@link #MAX_ACTIVE_SESSIONS}개를 넘으면
     * 가장 오래된 세션부터 지운다.
     *
     * <p>같은 계정으로 정확히 동시에 로그인이 여러 번 들어오면 "저장 후 재조회" 과정이 서로의
     * 신규 세션을 못 보고 어긋나 초과분이 남을 수 있다. 저장 전에 사용자 행을 잠가 이 계정의
     * 세션 발급을 직렬화한다. 반환값은 쓰지 않고 잠금 자체가 목적이다.
     */
    @Transactional
    public void issueRefreshToken(User user, String rawToken, String deviceId, LocalDateTime expiresAt) {
        userRepository.findByIdForUpdate(user.getId());
        refreshTokenRepository.save(RefreshToken.create(user, rawToken, deviceId, expiresAt));
        evictOverflow(user.getId());
    }

    private void evictOverflow(Long userId) {
        List<RefreshToken> active = refreshTokenRepository.findActiveByUserId(userId, LocalDateTime.now(clock));
        if (active.size() > MAX_ACTIVE_SESSIONS) {
            // active는 최신순(createdAt DESC) — MAX_ACTIVE_SESSIONS 뒤쪽이 가장 오래된 것들이다.
            List<RefreshToken> overflow = active.subList(MAX_ACTIVE_SESSIONS, active.size());
            refreshTokenRepository.deleteAll(overflow);
        }
    }

    public List<SessionResponse> listSessions(String userId, String currentRawToken) {
        List<RefreshToken> active = refreshTokenRepository.findActiveByUserId(Long.parseLong(userId), LocalDateTime.now(clock));
        return active.stream()
                .map(token -> SessionResponse.from(token, token.getToken().equals(currentRawToken)))
                .toList();
    }

    @Transactional
    public void revokeSession(String userId, Long sessionId) {
        int deleted = refreshTokenRepository.deleteByIdAndUserId(sessionId, Long.parseLong(userId));
        if (deleted == 0) {
            throw new BusinessException("세션을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public void revokeAllSessions(String userId) {
        refreshTokenRepository.deleteByUserId(Long.parseLong(userId));
    }
}
