package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResult;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.entity.RefreshToken;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.ratelimit.SimpleRateLimiter;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    // 계정 키가 주 방어선(재인증과 같은 한도), IP 키는 공용 IP 뒤 다수 사용자를 고려해 느슨하게.
    private static final int LOGIN_ID_ATTEMPT_LIMIT = 5;
    private static final int LOGIN_IP_ATTEMPT_LIMIT = 30;
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final UserConsentRecorder userConsentRecorder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FileService fileService;
    private final EmailVerificationService emailVerificationService;
    private final SimpleRateLimiter rateLimiter;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException("이미 사용 중인 아이디입니다.", HttpStatus.CONFLICT);
        }

        emailVerificationService.validateAndConsume(request.emailVerificationToken(), request.email());

        // 회원가입은 비로그인 요청이므로 비로그인 발급 경로(profiles/temp/)의 키만 허용된다.
        fileService.validateProfileImageKey(null, request.profileImageKey());

        User created = User.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.profileImageKey()
        );
        created.assignEmail(request.email());
        User user = userRepository.save(created);

        userConsentRecorder.recordSignupConsents(user, Boolean.TRUE.equals(request.marketingAgreed()));

        return SignupResponse.from(user).withImageUrl(fileService.resolveImageUrl(user.getProfileImageUrl()));
    }

    @Transactional
    public LoginResult login(LoginRequest request, String clientIp) {
        // BCrypt 비교 전에 차단해 무제한 온라인 추측과 해시 연산 부하를 막는다.
        // 계정 존재 여부가 드러나지 않도록 한도 초과 응답은 일반 메시지를 쓴다.
        requireLoginQuota("login:id:" + request.loginId(), LOGIN_ID_ATTEMPT_LIMIT);
        requireLoginQuota("login:ip:" + clientIp, LOGIN_IP_ATTEMPT_LIMIT);

        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        String accessToken = jwtUtil.generateToken(user.getId());
        String rawRefreshToken = jwtUtil.generateRefreshToken();
        sessionService.issueRefreshToken(user, rawRefreshToken, request.deviceId(), jwtUtil.refreshTokenExpiresAt());
        return new LoginResult(accessToken, rawRefreshToken);
    }

    @Transactional
    public LoginResult refresh(String rawToken) {
        if (rawToken == null) {
            throw new BusinessException("리프레시 토큰이 없습니다.", HttpStatus.UNAUTHORIZED);
        }

        RefreshToken token = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BusinessException("유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED));

        if (token.isUsed()) {
            // 재사용 감지 — 해당 사용자의 모든 토큰 삭제
            refreshTokenRepository.deleteByUserId(token.getUser().getId());
            throw new BusinessException("유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new BusinessException("만료된 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        // markAsUsedIfActive가 영속성 컨텍스트를 비우므로 필요한 값은 먼저 읽어 둔다.
        User user = token.getUser();
        String deviceId = token.getDeviceId();

        // 위 isUsed 확인은 스냅샷일 뿐이다. 같은 토큰의 동시 요청이 둘 다 통과하면 재사용
        // 감지가 무력화되므로, UPDATE의 갱신 행 수로 단 한 요청만 통과시키고 경합에서 진
        // 쪽은 재사용과 동일하게 취급한다.
        if (refreshTokenRepository.markAsUsedIfActive(token.getId()) != 1) {
            refreshTokenRepository.deleteByUserId(user.getId());
            throw new BusinessException("유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        String newRawToken = jwtUtil.generateRefreshToken();
        sessionService.issueRefreshToken(user, newRawToken, deviceId, jwtUtil.refreshTokenExpiresAt());

        return new LoginResult(jwtUtil.generateToken(user.getId()), newRawToken);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null) {
            return;
        }
        refreshTokenRepository.findByToken(rawToken).ifPresent(token -> {
            if (!token.isUsed()) {
                refreshTokenRepository.delete(token);
            }
        });
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return org.springframework.security.core.userdetails.User.builder()
                .username(userId)
                .password(user.getPassword() != null ? user.getPassword() : "")
                .roles("USER")
                .build();
    }

    private void requireLoginQuota(String key, int limit) {
        if (!rateLimiter.tryAcquire(key, limit, LOGIN_ATTEMPT_WINDOW)) {
            throw new BusinessException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
