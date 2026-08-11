package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.AppleReauthRequest;
import com.todo.domain.auth.dto.request.ReauthRequest;
import com.todo.domain.auth.dto.response.ReauthResponse;
import com.todo.domain.auth.entity.AuthProvider;
import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.entity.ReauthToken;
import com.todo.domain.auth.repository.ReauthTokenRepository;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService;
import com.todo.domain.auth.service.apple.AppleIdentityTokenService.VerifyResult;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.ratelimit.SimpleRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 민감한 작업 직전에 비밀번호를 다시 확인하고, 그 증명으로 1회용 토큰을 발급한다.
 *
 * <p>계정 탈취 상황에서 JWT만으로 되돌릴 수 없는 작업(탈퇴 등)이 실행되는 것을 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReauthService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private static final String RATE_LIMIT_PREFIX = "reauth:password:";
    private static final String APPLE_RATE_LIMIT_PREFIX = "reauth:apple:";
    private static final int PASSWORD_ATTEMPT_LIMIT = 5;
    private static final Duration PASSWORD_ATTEMPT_WINDOW = Duration.ofMinutes(1);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final UserRepository userRepository;
    private final ReauthTokenRepository reauthTokenRepository;
    private final AppleIdentityTokenService appleIdentityTokenService;
    private final PasswordEncoder passwordEncoder;
    private final SimpleRateLimiter rateLimiter;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    @Transactional
    public ReauthResponse reauthenticate(String userId, ReauthRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        // 소셜 계정은 password가 null이라 아래 비교가 의미 없이 실패한다. 무슨 일인지 알 수 없는
        // 401 대신, 애플 재인증 경로를 쓰라고 분명히 알려준다.
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new BusinessException("소셜 로그인 계정은 비밀번호로 재인증할 수 없습니다", HttpStatus.BAD_REQUEST);
        }

        if (!rateLimiter.tryAcquire(RATE_LIMIT_PREFIX + user.getId(), PASSWORD_ATTEMPT_LIMIT, PASSWORD_ATTEMPT_WINDOW)) {
            throw new BusinessException("잠시 후 다시 시도해 주세요", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED);
        }

        return issueReauthToken(user, request.purpose());
    }

    /**
     * Apple 계정의 재인증.
     *
     * <p>비밀번호가 없으므로 Apple 인증 시트를 새로 통과했다는 증거(identity token)를 요구한다.
     * 시트는 Face ID/암호를 거치므로 비밀번호 확인보다 약하지 않다. 검사를 건너뛰고 통과시키면
     * 토큰만 탈취해도 탈퇴가 가능해져 재인증을 두는 의미가 사라진다.
     */
    @Transactional
    public ReauthResponse reauthenticateWithApple(String userId, AppleReauthRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        if (user.getProvider() != AuthProvider.APPLE) {
            throw new BusinessException("Apple 계정이 아닙니다", HttpStatus.BAD_REQUEST);
        }

        if (!rateLimiter.tryAcquire(APPLE_RATE_LIMIT_PREFIX + user.getId(), PASSWORD_ATTEMPT_LIMIT, PASSWORD_ATTEMPT_WINDOW)) {
            throw new BusinessException("잠시 후 다시 시도해 주세요", HttpStatus.TOO_MANY_REQUESTS);
        }

        VerifyResult verified = appleIdentityTokenService.verify(request.identityToken(), request.nonce());

        // 남의 Apple 계정으로 받은 토큰을 자기 탈퇴에 쓰지 못하게 소유자를 확인한다.
        if (!verified.socialId().equals(user.getSocialId())) {
            throw new BusinessException("로그인한 계정과 다른 Apple 계정입니다", HttpStatus.UNAUTHORIZED);
        }

        return issueReauthToken(user, request.purpose());
    }

    private ReauthResponse issueReauthToken(User user, ReauthPurpose purpose) {
        // 재발급하면 같은 용도의 이전 토큰은 무효가 된다.
        reauthTokenRepository.deleteByUserIdAndPurpose(user.getId(), purpose);

        String rawToken = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(TOKEN_TTL);
        reauthTokenRepository.save(ReauthToken.create(user, hash(rawToken), purpose, expiresAt));

        return ReauthResponse.of(rawToken, expiresAt);
    }

    /**
     * 토큰을 검증하고 즉시 소비한다. 같은 토큰으로 두 번 통과할 수 없다.
     *
     * @return 재인증을 통과한 사용자 ID
     */
    @Transactional
    public Long consume(String userId, String rawToken, ReauthPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("재인증이 필요합니다", HttpStatus.UNAUTHORIZED);
        }

        ReauthToken token = reauthTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException("재인증이 필요합니다", HttpStatus.UNAUTHORIZED));

        // 남의 토큰을 자기 요청에 쓰지 못하게 소유자를 확인한다.
        if (!token.getUser().getId().toString().equals(userId)) {
            throw new BusinessException("재인증이 필요합니다", HttpStatus.UNAUTHORIZED);
        }
        if (!token.matchesPurpose(purpose)) {
            throw new BusinessException("이 작업에 사용할 수 없는 재인증입니다", HttpStatus.UNAUTHORIZED);
        }
        if (token.isUsed()) {
            throw new BusinessException("이미 사용된 재인증입니다", HttpStatus.UNAUTHORIZED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (token.isExpired(now)) {
            throw new BusinessException("재인증이 만료되었습니다", HttpStatus.UNAUTHORIZED);
        }

        // markAsUsed가 영속성 컨텍스트를 비우므로 그 전에 읽어 둔다.
        Long ownerId = token.getUser().getId();

        // 실제 1회용 보장은 여기서 이뤄진다. 위 검증과 이 UPDATE 사이에 다른 요청이 먼저
        // 소비했다면 갱신 행이 0이 되고, 그 요청만 통과한다.
        if (reauthTokenRepository.markAsUsed(token.getId(), now) != 1) {
            throw new BusinessException("이미 사용된 재인증입니다", HttpStatus.UNAUTHORIZED);
        }

        return ownerId;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 조회 키이자 저장 형태. 토큰이 이미 고엔트로피 난수라 느린 해시가 필요 없고,
     * 조회에 쓰이므로 salt 없이 결정적이어야 한다.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
